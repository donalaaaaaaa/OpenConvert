package com.openconvert.app.domain.planner

import com.openconvert.app.domain.converter.EncodeMode
import com.openconvert.app.domain.converter.StreamCodecs
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 硬件事实的测试替身。 */
private class FakeHardware(
    override val hasH264HardwareEncoder: Boolean = true,
    override val hasVp8HardwareEncoder: Boolean = true,
) : HardwareFacts

private val ROOMY = RuntimeFacts(usableScratchBytes = 64L * 1024 * 1024 * 1024)

private fun request(
    input: FileFormat,
    target: FileFormat,
    inputBytes: Long = 10L * 1024 * 1024,
    quality: QualityPreset = QualityPreset.BALANCED,
    resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    codecs: StreamCodecs? = null,
    hardware: HardwareFacts = FakeHardware(),
    runtime: RuntimeFacts = ROOMY,
) = PlanRequest(
    input = input,
    target = target,
    inputBytes = inputBytes,
    quality = quality,
    resolution = resolution,
    codecs = codecs,
    hardware = hardware,
    runtime = runtime,
)

private fun readyPlan(req: PlanRequest): ConversionPlan {
    val result = ConversionPlanner.plan(req)
    assertTrue("expected Ready but got $result", result is PlanResult.Ready)
    return (result as PlanResult.Ready).plan
}

private fun rejection(req: PlanRequest): PlanRejection {
    val result = ConversionPlanner.plan(req)
    assertTrue("expected Rejected but got $result", result is PlanResult.Rejected)
    return (result as PlanResult.Rejected).rejection
}

class ConversionPlannerEngineTest {

    @Test
    fun `image conversion uses libvips with bitmap fallback`() {
        val plan = readyPlan(request(FileFormat.PNG, FileFormat.WEBP))
        assertEquals(EngineType.LIBVIPS, plan.primaryEngine)
        assertEquals(EngineType.BITMAP_FACTORY, plan.fallbackEngine)
        assertNull(plan.encodeMode)
    }

    @Test
    fun `office conversion uses LOKit with no fallback`() {
        val plan = readyPlan(request(FileFormat.DOCX, FileFormat.PDF))
        assertEquals(EngineType.LIBREOFFICE_KIT, plan.primaryEngine)
        assertNull(plan.fallbackEngine)
    }

    @Test
    fun `archive conversion is stream copy and parallel`() {
        val plan = readyPlan(request(FileFormat.ZIP, FileFormat.TAR))
        assertEquals(EngineType.COMMONS_COMPRESS, plan.primaryEngine)
        assertTrue(plan.isStreamCopy)
        assertEquals(ConcurrencySlot.PARALLEL, plan.concurrency)
    }

    @Test
    fun `matching codecs remux instead of re-encoding`() {
        // MKV(H.264 + AAC) → MP4：容器兼容，直接换壳。
        val plan = readyPlan(
            request(
                FileFormat.MKV,
                FileFormat.MP4,
                codecs = StreamCodecs(videoCodec = "h264", audioCodec = "aac"),
            ),
        )
        assertEquals(EncodeMode.REMUX, plan.encodeMode)
        assertTrue(plan.isStreamCopy)
        assertEquals(ConcurrencySlot.PARALLEL, plan.concurrency)
    }

    @Test
    fun `hevc to h264 uses MediaCodec when hardware encoder exists`() {
        val plan = readyPlan(
            request(
                FileFormat.MKV,
                FileFormat.MP4,
                // HEVC 在 mp4Video 白名单里，但 SMALL 质量禁止 remux → 走重编码。
                quality = QualityPreset.SMALL,
                codecs = StreamCodecs(videoCodec = "hevc", audioCodec = "aac"),
                hardware = FakeHardware(hasH264HardwareEncoder = true),
            ),
        )
        assertEquals(EngineType.MEDIA3_MEDIACODEC, plan.primaryEngine)
        assertEquals(EngineType.FFMPEG_KIT, plan.fallbackEngine)
        assertEquals(EncodeMode.HARDWARE_H264, plan.encodeMode)
    }

    @Test
    fun `no h264 hardware encoder falls back to software mpeg4`() {
        val plan = readyPlan(
            request(
                FileFormat.MKV,
                FileFormat.MP4,
                quality = QualityPreset.SMALL,
                codecs = StreamCodecs(videoCodec = "hevc", audioCodec = "aac"),
                hardware = FakeHardware(hasH264HardwareEncoder = false),
            ),
        )
        assertEquals(EngineType.FFMPEG_KIT, plan.primaryEngine)
        assertEquals(EncodeMode.SOFTWARE_MPEG4, plan.encodeMode)
        assertTrue(plan.reason.contains("无 H.264 硬件编码器"))
    }

    @Test
    fun `webm target uses LiTr when vp8 hardware encoder exists`() {
        val plan = readyPlan(
            request(
                FileFormat.MP4,
                FileFormat.WEBM,
                codecs = StreamCodecs(videoCodec = "h264", audioCodec = "aac"),
                hardware = FakeHardware(hasVp8HardwareEncoder = true),
            ),
        )
        assertEquals(EngineType.LITR, plan.primaryEngine)
        assertEquals(EncodeMode.LITR_VP8, plan.encodeMode)
    }

    @Test
    fun `webm target without vp8 hardware uses ffmpeg fast vp8`() {
        val plan = readyPlan(
            request(
                FileFormat.MP4,
                FileFormat.WEBM,
                codecs = StreamCodecs(videoCodec = "h264", audioCodec = "aac"),
                hardware = FakeHardware(hasVp8HardwareEncoder = false),
            ),
        )
        assertEquals(EngineType.FFMPEG_KIT, plan.primaryEngine)
        assertEquals(EncodeMode.FAST_VP8, plan.encodeMode)
    }

    @Test
    fun `same-codec audio conversion copies the stream`() {
        // M4A(AAC) → AAC 是纯换容器。
        val plan = readyPlan(
            request(
                FileFormat.M4A,
                FileFormat.AAC,
                codecs = StreamCodecs(audioCodec = "aac"),
            ),
        )
        assertEquals(EncodeMode.AUDIO_COPY, plan.encodeMode)
        assertTrue(plan.isStreamCopy)
    }

    @Test
    fun `different-codec audio conversion re-encodes`() {
        val plan = readyPlan(
            request(
                FileFormat.FLAC,
                FileFormat.MP3,
                codecs = StreamCodecs(audioCodec = "flac"),
            ),
        )
        assertEquals(EncodeMode.AUDIO_ONLY, plan.encodeMode)
        assertEquals(false, plan.isStreamCopy)
    }
}
