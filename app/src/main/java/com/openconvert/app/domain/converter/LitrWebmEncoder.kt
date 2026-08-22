package com.openconvert.app.domain.converter

import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.linkedin.android.litr.MediaTransformer
import com.linkedin.android.litr.TransformationListener
import com.linkedin.android.litr.TransformationOptions
import com.linkedin.android.litr.analytics.TrackTransformationInfo
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class LitrWebmEncoder(
    context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val appContext = context.applicationContext

    suspend fun convert(task: ConversionTask): ConversionResult = withContext(Dispatchers.IO) {
        val sourceUri = Uri.parse(task.sourceUri)
        val outputUri = task.outputUri?.let(Uri::parse)
            ?: return@withContext ConversionResult.Failure("没有选择输出文件")

        val meta = readSourceMeta(sourceUri)
            ?: return@withContext ConversionResult.Failure("无法读取视频尺寸，请换一个文件重试")
        val (width, height) = LitrWebmFormats.scaledSize(meta.width, meta.height, task.resolution)
        val videoFormat = MediaFormat.createVideoFormat(LitrWebmFormats.VIDEO_MIME, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, LitrWebmFormats.videoBitrateBps(task.quality, meta.bitrate))
            setInteger(MediaFormat.KEY_FRAME_RATE, meta.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, ANDROID_COLOR_FORMAT_SURFACE)
        }
        val audioFormat = MediaFormat.createAudioFormat(audioMime(), meta.sampleRate, meta.channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, LitrWebmFormats.audioBitrateBps(task.quality))
        }

        val transformer = MediaTransformer(appContext)
        val progressScope = CoroutineScope(coroutineContext)
        try {
            reportProgress(8)
            suspendCancellableCoroutine<ConversionResult> { continuation ->
                val listener = object : TransformationListener {
                    override fun onStarted(id: String) = Unit

                    override fun onProgress(id: String, progress: Float) {
                        if (!continuation.isActive) return
                        val mapped = (8 + progress.coerceIn(0f, 1f) * 86f).roundToInt()
                        progressScope.launch { reportProgress(mapped) }
                    }

                    override fun onCompleted(id: String, infos: List<TrackTransformationInfo>?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                ConversionResult.Success(
                                    outputUri.toString(),
                                    outputSize(outputUri),
                                    EngineType.LITR,
                                ),
                            )
                        }
                    }

                    override fun onCancelled(id: String, infos: List<TrackTransformationInfo>?) {
                        if (continuation.isActive) continuation.resume(ConversionResult.Cancelled)
                    }

                    override fun onError(id: String, cause: Throwable?, infos: List<TrackTransformationInfo>?) {
                        if (continuation.isActive) {
                            continuation.resume(
                                ConversionResult.Failure(
                                    cause?.message?.take(180) ?: "MediaCodec VP8 转换失败",
                                    cause,
                                ),
                            )
                        }
                    }
                }
                continuation.invokeOnCancellation { transformer.cancel(task.id) }
                transformer.transform(
                    task.id,
                    sourceUri,
                    outputUri,
                    videoFormat,
                    audioFormat,
                    listener,
                    TransformationOptions.Builder().setGranularity(100).setRemoveMetadata(true).build(),
                )
            }
        } catch (cancelled: CancellationException) {
            runCatching { transformer.cancel(task.id) }
            throw cancelled
        } finally {
            transformer.release()
        }
    }

    private fun audioMime(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LitrWebmFormats.AUDIO_MIME_OPUS
        } else {
            LitrWebmFormats.AUDIO_MIME_VORBIS
        }

    private fun readSourceMeta(uri: Uri): SourceMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width == null || height == null || width <= 0 || height <= 0) return null
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?.roundToInt()
                ?.coerceIn(15, 60)
                ?: 30
            SourceMeta(width, height, bitrate, frameRate)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun outputSize(uri: Uri): Long =
        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize.coerceAtLeast(0L) } ?: 0L

    private suspend fun reportProgress(progress: Int) {
        coroutineContext.ensureActive()
        onProgress(progress.coerceIn(0, 100))
    }

    private data class SourceMeta(
        val width: Int,
        val height: Int,
        val bitrate: Long?,
        val frameRate: Int,
        val sampleRate: Int = 48_000,
        val channelCount: Int = 2,
    )

    private companion object {
        const val ANDROID_COLOR_FORMAT_SURFACE = 0x7F000789
    }
}
