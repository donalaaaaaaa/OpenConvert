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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaConverterInstrumentedTest {
    @Test
    fun convertsWavToMp3WithNativeFfmpeg() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val sourceUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "openconvert-source-$testId.wav")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OpenConvertTest")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "openconvert-output-$testId.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OpenConvertTest")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                },
            ),
        )

        try {
            resolver.openOutputStream(sourceUri, "wt")!!.use { it.write(createTestWav()) }
            resolver.update(
                sourceUri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )

            val progress = mutableListOf<Int>()
            val result = MediaConverter(context) { progress += it }.convert(
                ConversionTask(
                    id = testId,
                    sourceUri = sourceUri.toString(),
                    sourceName = "openconvert-source.wav",
                    sourceFormat = FileFormat.WAV,
                    targetFormat = FileFormat.MP3,
                    outputUri = outputUri.toString(),
                    quality = QualityPreset.BALANCED,
                    resolution = ResolutionPreset.ORIGINAL,
                ),
            )

            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            assertTrue(resolver.openFileDescriptor(outputUri, "r")!!.use { it.statSize } > 0L)
            assertTrue(progress.lastOrNull() == 100)
            val duration = MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, outputUri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            }
            assertTrue("Expected a playable MP3", duration >= 400L)
        } finally {
            runCatching { resolver.delete(sourceUri, null, null) }
            runCatching { resolver.delete(outputUri, null, null) }
        }
    }

    @Test
    fun convertsMp4ToHalfSizeWebmWithNativeFfmpeg() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val generatedInput = File(context.cacheDir, "openconvert-video-$testId.mp4")
        val generation = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y",
                "-f", "lavfi",
                "-i", "color=c=blue:s=160x120:d=1",
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
                    put(MediaStore.Video.Media.DISPLAY_NAME, "openconvert-source-$testId.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OpenConvertTest")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "openconvert-output-$testId.webm")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/webm")
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

            val result = MediaConverter(context).convert(
                ConversionTask(
                    id = testId,
                    sourceUri = sourceUri.toString(),
                    sourceName = "openconvert-source.mp4",
                    sourceFormat = FileFormat.MP4,
                    targetFormat = FileFormat.WEBM,
                    outputUri = outputUri.toString(),
                    quality = QualityPreset.SMALL,
                    resolution = ResolutionPreset.SMALL,
                ),
            )

            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, outputUri)
                val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                assertTrue("Expected 80x60 WEBM, got ${width}x$height", width == 80 && height == 60)
                assertTrue("Expected a playable WEBM", duration >= 800L)
            } finally {
                metadata.release()
            }
        } finally {
            generatedInput.delete()
            runCatching { resolver.delete(sourceUri, null, null) }
            runCatching { resolver.delete(outputUri, null, null) }
        }
    }

    private fun createTestWav(): ByteArray {
        val sampleRate = 44_100
        val sampleCount = sampleRate / 2
        val pcm = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { index ->
            val sample = (sin(2.0 * PI * 440.0 * index / sampleRate) * Short.MAX_VALUE * 0.25).toInt()
            pcm.putShort(sample.toShort())
        }
        val data = pcm.array()
        return ByteArrayOutputStream(44 + data.size).apply {
            write("RIFF".toByteArray())
            writeIntLe(36 + data.size)
            write("WAVEfmt ".toByteArray())
            writeIntLe(16)
            writeShortLe(1)
            writeShortLe(1)
            writeIntLe(sampleRate)
            writeIntLe(sampleRate * 2)
            writeShortLe(2)
            writeShortLe(16)
            write("data".toByteArray())
            writeIntLe(data.size)
            write(data)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }
}
