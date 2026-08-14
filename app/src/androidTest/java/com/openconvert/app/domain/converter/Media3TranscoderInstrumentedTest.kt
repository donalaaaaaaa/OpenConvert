package com.openconvert.app.domain.converter

import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 路线图：视频主引擎 Media3/MediaCodec。
 *
 * 源是 MKV(MPEG-4 Part 2)，质量=SMALL 强制重新编码（planner 对 SMALL
 * 永不 remux/copy），于是 plan = HARDWARE_H264，
 * MediaConverter 优先交给 Media3Transcoder。
 * （MP4→MP4 自身压缩当前不在产品矩阵内，canConvertLocallyTo 拒绝同格式。）
 */
@RunWith(AndroidJUnit4::class)
class Media3TranscoderInstrumentedTest {
    @Test
    fun convertsMkvToCompressedMp4WithMedia3() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val generatedInput = File(context.cacheDir, "openconvert-m3-$testId.mkv")
        val generation = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y",
                "-f", "lavfi",
                "-i", "color=c=green:s=176x128:d=1",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=1",
                "-c:v", "mpeg4",
                "-c:a", "aac",
                "-shortest",
                generatedInput.absolutePath,
            ),
        )
        assertTrue(
            "Unable to generate test MP4: ${generation.output}",
            ReturnCode.isSuccess(generation.returnCode),
        )

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val sourceUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "openconvert-m3-src-$testId.mkv")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/x-matroska")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OpenConvertTest")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "openconvert-m3-out-$testId.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OpenConvertTest")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                },
            ),
        )

        try {
            resolver.openOutputStream(sourceUri, "wt")!!.use { output ->
                generatedInput.inputStream().use { input -> input.copyTo(output) }
            }
            resolver.update(
                sourceUri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )

            val progress = mutableListOf<Int>()
            val result = MediaConverter(context) { progress += it }.convert(
                ConversionTask(
                    id = testId,
                    sourceUri = sourceUri.toString(),
                    sourceName = "openconvert-m3-src.mkv",
                    sourceFormat = FileFormat.MKV,
                    targetFormat = FileFormat.MP4,
                    outputUri = outputUri.toString(),
                    fileSize = generatedInput.length(),
                    quality = QualityPreset.SMALL,
                    resolution = ResolutionPreset.ORIGINAL,
                ),
            )

            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, outputUri)
                val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                // MediaCodec requires 16-px alignment; allow one padding step on top of the source.
                assertTrue(
                    "Expected ~176x128 MP4, got ${width}x$height",
                    width != null && width in 176..192 && height != null && height in 128..144,
                )
                assertTrue("Expected a playable MP4, duration=$duration", duration >= 800L)
            } finally {
                metadata.release()
            }
            assertTrue("progress should reach 100", progress.lastOrNull() == 100)
        } finally {
            generatedInput.delete()
            runCatching { resolver.delete(sourceUri, null, null) }
            runCatching { resolver.delete(outputUri, null, null) }
        }
    }
}
