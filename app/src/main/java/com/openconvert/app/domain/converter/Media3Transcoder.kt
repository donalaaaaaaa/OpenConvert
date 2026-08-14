package com.openconvert.app.domain.converter

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.openconvert.app.domain.model.ResolutionPreset
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 视频转码主引擎（路线图：MediaCodec/Media3 优先，FFmpeg 兜底）。
 *
 * 只做 MP4 目标 + 需要重编码的场景：MediaCodec H.264 硬件编码 + AAC。
 * Remux / 拷流 / WEBM(LiTr) / 音频由其它路径处理，不进这里。
 */
@UnstableApi
class Media3Transcoder(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    sealed interface Outcome {
        data object Success : Outcome
        data object Cancelled : Outcome
        data class Failure(val message: String) : Outcome
    }

    suspend fun transcode(
        sourceUri: Uri,
        outputFile: File,
        videoBitrateBps: Int?,
        resolution: ResolutionPreset,
    ): Outcome = withContext(Dispatchers.IO) {
        val thread = HandlerThread("openconvert-media3").apply { start() }
        try {
            runOnLooper(thread.looper, sourceUri, outputFile, videoBitrateBps, resolution)
        } finally {
            thread.quitSafely()
        }
    }

    private suspend fun runOnLooper(
        looper: Looper,
        sourceUri: Uri,
        outputFile: File,
        videoBitrateBps: Int?,
        resolution: ResolutionPreset,
    ): Outcome {
        val scope = CoroutineScope(kotlin.coroutines.coroutineContext)
        val handler = Handler(looper)
        val transformerRef = AtomicReference<Transformer?>(null)

        return try {
            suspendCancellableCoroutine { continuation ->
                // Media3 binds the Transformer to its Looper: every API call
                // (build/start/getProgress/cancel) must run on that thread.
                handler.post {
                    val progress = AtomicInteger(15)
                    val encoderFactoryBuilder = DefaultEncoderFactory.Builder(context)
                    videoBitrateBps?.takeIf { it > 0 }?.let { bps ->
                        encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(bps).build(),
                        )
                    }
                    val transformer = Transformer.Builder(context)
                        .setLooper(looper)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactoryBuilder.build())
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (continuation.isActive) continuation.resume(Outcome.Success)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuation.isActive) {
                                        val message = exportException.errorCodeName.ifBlank {
                                            exportException.message ?: "Media3 export failed"
                                        }
                                        continuation.resume(Outcome.Failure(message))
                                    }
                                }
                            },
                        )
                        .build()
                    transformerRef.set(transformer)

                    val editedBuilder = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
                    if (resolution != ResolutionPreset.ORIGINAL) {
                        val targetHeight = sourceHeight(sourceUri)?.let { sourceHeight ->
                            (sourceHeight * resolution.scalePercent / 100)
                                .let { if (it % 2 == 1) it + 1 else it }
                                .coerceAtLeast(2)
                        }
                        targetHeight?.let {
                            editedBuilder.setEffects(
                                Effects(emptyList(), listOf(Presentation.createForHeight(it))),
                            )
                        }
                    }
                    transformer.start(editedBuilder.build(), outputFile.absolutePath)

                    val holder = ProgressHolder()
                    val poller = object : Runnable {
                        override fun run() {
                            if (!continuation.isActive) return
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val fraction = holder.progress / 100.0
                                // Keep inside the shared 15..92 band used by MediaConverter.
                                val next = (15 + fraction * 77).toInt().coerceIn(15, 92)
                                val previous = progress.getAndUpdate { old -> maxOf(old, next) }
                                if (next > previous) {
                                    scope.launch { onProgress(next) }
                                }
                            }
                            handler.postDelayed(this, 500)
                        }
                    }
                    handler.postDelayed(poller, 500)
                }

                continuation.invokeOnCancellation {
                    handler.post { transformerRef.get()?.cancel() }
                }
            }
        } catch (_: CancellationException) {
            handler.post { transformerRef.get()?.cancel() }
            Outcome.Cancelled
        }
    }

    private fun sourceHeight(uri: Uri): Int? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_HEIGHT)) {
                    return@runCatching format.getInteger(MediaFormat.KEY_HEIGHT)
                }
            }
            null
        } finally {
            extractor.release()
        }
    }.getOrNull()

    companion object {
        /** "1234k" (FFmpeg style) → 1_234_000 bps; plain numbers pass through. */
        fun parseBitrateBps(spec: String?): Int? {
            val trimmed = spec?.trim()?.lowercase() ?: return null
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.endsWith("k") -> trimmed.dropLast(1).toLongOrNull()?.times(1_000L)
                trimmed.endsWith("m") -> trimmed.dropLast(1).toLongOrNull()?.times(1_000_000L)
                else -> trimmed.toLongOrNull()
            }?.takeIf { it > 0 }?.toInt()
        }
    }
}
