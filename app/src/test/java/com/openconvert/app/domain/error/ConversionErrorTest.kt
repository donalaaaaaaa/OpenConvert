package com.openconvert.app.domain.error

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.planner.PlanRejection
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionErrorTest {

    @Test
    fun exceptionCarriesStableCode() {
        assertEquals(
            ConversionError.Code.INSUFFICIENT_STORAGE,
            ConversionException.InsufficientStorage(10, 1).code,
        )
        assertEquals(
            ConversionError.Code.CODEC_UNAVAILABLE,
            ConversionException.CodecUnavailable("av1").code,
        )
        assertEquals(
            ConversionError.Code.TASK_CANCELLED,
            ConversionException.wrap(kotlinx.coroutines.CancellationException()).code,
        )
    }

    @Test
    fun wrapMapsCommonThrowables() {
        assertEquals(
            ConversionError.Code.PERMISSION_DENIED,
            ConversionException.wrap(SecurityException()).code,
        )
        assertEquals(
            ConversionError.Code.INVALID_FILE,
            ConversionException.wrap(java.io.FileNotFoundException("gone")).code,
        )
    }

    @Test
    fun rejectionMapsToCode() {
        assertEquals(
            ConversionError.Code.INSUFFICIENT_STORAGE,
            ConversionError.fromRejection(PlanRejection.InsufficientSpace(2, 1)),
        )
        assertEquals(
            ConversionError.Code.CODEC_UNAVAILABLE,
            ConversionError.fromRejection(
                PlanRejection.NoUsableEncoder("h264", listOf(EngineType.MEDIA3_MEDIACODEC)),
            ),
        )
    }

    @Test
    fun legacyMessageGuessesCode() {
        assertEquals(ConversionError.Code.INSUFFICIENT_STORAGE, ConversionError.fromMessage("存储空间不足，请清理后再试"))
        assertEquals(ConversionError.Code.INTERRUPTED, ConversionError.fromMessage("上次转换被系统中断，请重试"))
        assertEquals(ConversionError.Code.UNKNOWN, ConversionError.fromMessage("FFmpeg exited with code 234"))
        assertEquals(ConversionError.Code.UNKNOWN, ConversionError.fromMessage(null))
    }

    @Test
    fun parseIgnoresUnknownTokens() {
        assertEquals(ConversionError.Code.ENGINE_FAILURE, ConversionError.parse("ENGINE_FAILURE"))
        assertEquals(null, ConversionError.parse("FUTURE_CODE"))
        assertEquals(null, ConversionError.parse(null))
    }
}
