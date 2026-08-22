package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.canConvertLocallyTo
import com.openconvert.app.domain.planner.ConversionPlan
import com.openconvert.app.domain.device.PersistentCodecBlacklist
import com.openconvert.app.domain.work.BoundedIo
import com.openconvert.app.domain.work.StorageGuard
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MediaConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) : Converter {
    private val resolver: ContentResolver = context.contentResolver

    override fun supports(inputFormat: FileFormat, outputFormat: FileFormat): Boolean =
        inputFormat.category in setOf(FileCategory.AUDIO, FileCategory.VIDEO) &&
            inputFormat.canConvertLocallyTo(outputFormat)

    override suspend fun convert(task: ConversionTask): ConversionResult = convert(task, null, null)

    /**
     * 给 ConversionExecutor 的预检入口。Planner 与真正执行复用同一份编码事实，
     * 避免 Planner 认为需要重编码、执行层却自行走了流拷贝（Benchmark 也会随之失真）。
     */
    internal suspend fun inspectForPlanning(task: ConversionTask): StreamCodecs =
        withContext(Dispatchers.IO) { inspect(task) }

    internal suspend fun convert(
        task: ConversionTask,
        executionPlan: ConversionPlan?,
        preflightCodecs: StreamCodecs?,
    ): ConversionResult = withContext(Dispatchers.IO) {
        val outputUri = task.outputUri?.let(Uri::parse)
            ?: return@withContext ConversionResult.Failure("没有选择输出文件")
        if (!supports(task.sourceFormat, task.targetFormat)) {
            return@withContext ConversionResult.Failure(
                "暂不支持 ${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            )
        }

        val workDir = File(context.cacheDir, "media-conversions/${task.id}")
        val outputFile = File(workDir, "output.${task.targetFormat.preferredExtension}")

        try {
            if (!workDir.mkdirs() && !workDir.isDirectory) {
                throw IOException("无法创建音视频转换临时目录")
            }
            reportProgress(3)
            val codecs = preflightCodecs ?: inspect(task)
            val durationMs = codecs.durationMs ?: 0L
            reportProgress(15)
            var plan = MediaEncodePlanner.plan(task.targetFormat, task.quality, task.resolution, codecs)
            executionPlan?.encodeMode?.let { plannedMode ->
                // bitrate 仍由媒体 Planner 按源文件计算；执行模式以统一 ConversionPlanner 为准。
                if (plannedMode != plan.mode) plan = plan.copy(mode = plannedMode)
            }

            if (plan.mode == EncodeMode.LITR_VP8) {
                val skipVp8 = PersistentCodecBlacklist.get(context)
                    .shouldSkipHardware(android.os.Build.MANUFACTURER, android.os.Build.MODEL, "vp8")
                if (!skipVp8) {
                    when (val litr = LitrWebmEncoder(context, onProgress).convert(task)) {
                        is ConversionResult.Success -> return@withContext litr
                        ConversionResult.Cancelled -> return@withContext ConversionResult.Cancelled
                        is ConversionResult.Failure -> {
                            PersistentCodecBlacklist.get(context).record(
                                android.os.Build.MANUFACTURER,
                                android.os.Build.MODEL,
                                "vp8",
                            )
                        }
                    }
                }
            }

            // MP4 re-encode: Media3/MediaCodec is the primary engine; FFmpeg only as fallback.
            if (plan.mode == EncodeMode.HARDWARE_H264) {
                val skipH264 = PersistentCodecBlacklist.get(context)
                    .shouldSkipHardware(android.os.Build.MANUFACTURER, android.os.Build.MODEL, "h264")
                if (!skipH264) {
                    android.util.Log.i("OpenConvert", "engine=media3 task=${task.id}")
                    when (
                        val media3 = Media3Transcoder(context, onProgress).transcode(
                            sourceUri = Uri.parse(task.sourceUri),
                            outputFile = outputFile,
                            videoBitrateBps = Media3Transcoder.parseBitrateBps(plan.videoBitrate),
                            resolution = task.resolution,
                        )
                    ) {
                        Media3Transcoder.Outcome.Success -> {
                            android.util.Log.i("OpenConvert", "engine=media3 result=success task=${task.id}")
                            return@withContext finishOutput(
                                outputFile,
                                outputUri,
                                EngineType.MEDIA3_MEDIACODEC,
                            )
                        }
                        Media3Transcoder.Outcome.Cancelled -> return@withContext ConversionResult.Cancelled
                        is Media3Transcoder.Outcome.Failure -> {
                            PersistentCodecBlacklist.get(context).record(
                                android.os.Build.MANUFACTURER,
                                android.os.Build.MODEL,
                                "h264",
                            )
                            android.util.Log.w(
                                "OpenConvert",
                                "engine=media3 result=fallback reason=${media3.message} task=${task.id}",
                            )
                        }
                    }
                } else {
                    android.util.Log.i("OpenConvert", "engine=media3 skipped blacklist task=${task.id}")
                }
            }

            val inputPath = resolveInputPath(workDir, task)
            reportProgress(10)

            var arguments = MediaCommandBuilder.build(
                inputPath = inputPath,
                outputPath = outputFile.absolutePath,
                target = task.targetFormat,
                quality = task.quality,
                resolution = task.resolution,
                plan = plan,
            )
            var outcome = execute(arguments, durationMs.coerceAtLeast(0L), task.fileSize)
            val fallback = MediaEncodePlanner.fallback(plan)
            if (!outcome.success && !outcome.cancelled && fallback != null) {
                plan = fallback
                arguments = MediaCommandBuilder.build(
                    inputPath = inputPath,
                    outputPath = outputFile.absolutePath,
                    target = task.targetFormat,
                    quality = task.quality,
                    resolution = task.resolution,
                    plan = plan,
                )
                outcome = execute(arguments, durationMs.coerceAtLeast(0L), task.fileSize)
            }
            if (outcome.cancelled) return@withContext ConversionResult.Cancelled
            if (!outcome.success) {
                throw IOException(outcome.errorMessage.ifBlank { "FFmpeg 编码失败" })
            }
            finishOutput(outputFile, outputUri, EngineType.FFMPEG_KIT)
        } catch (cancelled: CancellationException) {
            deleteIncompleteOutput(outputUri)
            throw cancelled
        } catch (error: Throwable) {
            deleteIncompleteOutput(outputUri)
            ConversionResult.Failure(error.toUserMessage(), error)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun inspect(task: ConversionTask): StreamCodecs {
        val sourceUri = Uri.parse(task.sourceUri)
        val durationMs = readDurationMs(sourceUri)
        return if (shouldProbe(task)) {
            probe(sourceUri, task.fileSize, durationMs)
        } else {
            StreamCodecs(fileSize = task.fileSize, durationMs = durationMs)
        }
    }

    private suspend fun finishOutput(
        outputFile: File,
        outputUri: Uri,
        actualEngine: EngineType,
    ): ConversionResult {
        if (!outputFile.isFile || outputFile.length() <= 0L) {
            throw IOException("转换完成但没有生成有效文件")
        }
        reportProgress(94)
        resolver.openOutputStream(outputUri, "wt")?.use { output ->
            outputFile.inputStream().use { input -> BoundedIo.copy(input, output) }
        } ?: throw FileNotFoundException("无法写入目标文件")
        val outputSize = outputFile.length()
        reportProgress(100)
        return ConversionResult.Success(outputUri.toString(), outputSize, actualEngine)
    }

    private suspend fun execute(
        arguments: Array<String>,
        durationMs: Long,
        sourceBytes: Long,
    ): FFmpegOutcome {
        val callerScope = CoroutineScope(kotlin.coroutines.coroutineContext)
        val activeSession = AtomicReference<FFmpegSession?>()
        val lastProgress = AtomicInteger(15)

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { activeSession.get()?.cancel() }
            val session = FFmpegKit.executeWithArgumentsAsync(
                arguments,
                { completed ->
                    if (continuation.isActive) {
                        val code = completed.returnCode
                        continuation.resume(
                            FFmpegOutcome(
                                success = ReturnCode.isSuccess(code),
                                cancelled = ReturnCode.isCancel(code),
                                errorMessage = completed.failStackTrace.orEmpty()
                                    .ifBlank { completed.output.orEmpty().takeLast(MAX_ERROR_LENGTH) },
                            ),
                        )
                    }
                },
                null,
                { statistics ->
                    if (!continuation.isActive) return@executeWithArgumentsAsync
                    val timeMs = if (statistics.time > 0.0 && statistics.time < 1_000.0 && durationMs > 10_000L) {
                        statistics.time * 1_000.0
                    } else {
                        statistics.time
                    }
                    val timed = if (durationMs > 0L) {
                        timeMs / durationMs.toDouble()
                    } else {
                        0.0
                    }
                    val sized = if (sourceBytes > 0L) {
                        statistics.size.toDouble() / sourceBytes.toDouble()
                    } else {
                        0.0
                    }
                    val fraction = maxOf(timed, sized).coerceIn(0.0, 1.0)
                    val progress = if (fraction > 0.0) {
                        (15 + fraction * 77).roundToInt()
                    } else {
                        (lastProgress.get() + 1).coerceAtMost(20)
                    }
                    val previous = lastProgress.getAndUpdate { old -> maxOf(old, progress) }
                    if (progress > previous) callerScope.launch { reportProgress(progress) }
                },
            )
            activeSession.set(session)
            if (!continuation.isActive) session.cancel()
        }
    }

    /** 需要探测真实编码的场景：视频 remux/拷贝决策，以及音频同编码直拷决策（清单 §一）。 */
    private fun shouldProbe(task: ConversionTask): Boolean {
        val isAudioTarget = task.targetFormat.category == com.openconvert.app.domain.model.FileCategory.AUDIO
        if (isAudioTarget) return true
        return task.targetFormat == com.openconvert.app.domain.model.FileFormat.MP4 &&
            task.quality != com.openconvert.app.domain.model.QualityPreset.SMALL &&
            task.resolution == com.openconvert.app.domain.model.ResolutionPreset.ORIGINAL
    }

    private fun resolveInputPath(workDir: File, task: ConversionTask): String {
        val source = Uri.parse(task.sourceUri)
        val readableFilePath = source.takeIf { it.scheme == ContentResolver.SCHEME_FILE }
            ?.path
            ?.takeIf { path -> File(path).canRead() }
        val safParameter = runCatching {
            FFmpegKitConfig.getSafParameterForRead(context, source)
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val copiesInput = MediaInputResolver.copiesInput(
            hasReadableFilePath = readableFilePath != null,
            hasSafParameter = safParameter != null,
        )
        val required = StorageGuard.requiredScratchBytes(task.fileSize, copiesInput)
        if (!StorageGuard.hasEnoughSpace(workDir.usableSpace, required)) {
            throw IOException(StorageGuard.INSUFFICIENT_SPACE)
        }
        if (readableFilePath != null) return readableFilePath
        if (safParameter != null) return safParameter

        val destination = File(workDir, "input.${task.sourceFormat.preferredExtension}")
        resolver.openInputStream(source)?.use { input ->
            destination.outputStream().use { output -> BoundedIo.copy(input, output) }
        } ?: throw FileNotFoundException("无法读取源文件")
        if (destination.length() <= 0L) throw IOException("源文件为空")
        return destination.absolutePath
    }

    /**
     * 用系统 MediaExtractor 探测真实编码（清单 §一）。
     * 不用 ffprobe：ffprobe 会打开并关闭 SAF fd，导致随后 FFmpeg 读到已关闭的描述符。
     */
    private fun probe(uri: Uri, fileSize: Long, durationMs: Long): StreamCodecs {
        var videoCodec: String? = null
        var audioCodec: String? = null
        var videoBitrate: Long? = null
        runCatching {
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)?.lowercase() ?: continue
                    when {
                        mime.startsWith("video/") -> {
                            videoCodec = mapVideoCodec(mime)
                            if (videoBitrate == null && format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                                videoBitrate = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE).toLong()
                            }
                        }
                        mime.startsWith("audio/") -> audioCodec = mapAudioCodec(mime)
                    }
                }
            } finally {
                extractor.release()
            }
        }
        return StreamCodecs(
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            videoBitrate = videoBitrate,
            durationMs = durationMs,
            fileSize = fileSize,
        )
    }

    private fun mapVideoCodec(mime: String): String = when (mime) {
        android.media.MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
        android.media.MediaFormat.MIMETYPE_VIDEO_HEVC -> "hevc"
        android.media.MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
        android.media.MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
        android.media.MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        else -> mime.removePrefix("video/")
    }

    private fun mapAudioCodec(mime: String): String = when (mime) {
        android.media.MediaFormat.MIMETYPE_AUDIO_AAC,
        "audio/mp4a-latm", -> "aac"
        android.media.MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
        android.media.MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
        android.media.MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
        android.media.MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
        android.media.MediaFormat.MIMETYPE_AUDIO_RAW -> "pcm_s16le"
        else -> mime.removePrefix("audio/")
    }

    private fun readDurationMs(uri: Uri): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    private suspend fun reportProgress(progress: Int) {
        kotlin.coroutines.coroutineContext.ensureActive()
        onProgress(progress.coerceIn(0, 100))
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is SecurityException -> "没有读取或保存此文件的权限"
        is FileNotFoundException -> message ?: "找不到源文件或保存位置"
        is IOException -> {
            val lastLine = message?.lineSequence()?.lastOrNull()?.take(180).orEmpty()
            when {
                lastLine.contains("Permission denied", ignoreCase = true) ->
                    "无法读取源文件，请重新选择后重试"
                lastLine.isBlank() -> "音视频转换失败"
                else -> lastLine
            }
        }
        else -> "音视频转换失败，请确认文件未损坏后重试"
    }

    private fun deleteIncompleteOutput(uri: Uri) {
        runCatching {
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path?.let { File(it).delete() }
            } else {
                resolver.delete(uri, null, null)
            }
        }
    }

    private data class FFmpegOutcome(
        val success: Boolean,
        val cancelled: Boolean,
        val errorMessage: String,
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 2_000
    }
}
