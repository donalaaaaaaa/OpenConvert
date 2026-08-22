package com.openconvert.app.data.local

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversionEntityTest {

    private fun task() = ConversionTask(
        id = "engine-task",
        sourceUri = "content://input",
        sourceName = "movie.mov",
        sourceFormat = FileFormat.MOV,
        targetFormat = FileFormat.MP4,
        status = ConversionStatus.COMPLETED,
        actualEngine = EngineType.FFMPEG_KIT,
    )

    @Test
    fun `actual engine survives entity round trip`() {
        assertEquals(EngineType.FFMPEG_KIT, task().toEntity().toDomain().actualEngine)
    }

    @Test
    fun `unknown future engine does not break history loading`() {
        val future = task().toEntity().copy(actualEngine = "FUTURE_ENGINE")
        assertNull(future.toDomain().actualEngine)
    }
}
