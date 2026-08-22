package com.openconvert.app.domain.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟系统杀进程后的 Room 状态：任务停在 RUNNING，WorkManager 里已经没有对应 work。
 * 走与 [OpenConvertApplication.onCreate] 相同的 [OpenConvertApplication.recoverOrphans]。
 */
@RunWith(AndroidJUnit4::class)
class ConversionRecoveryInstrumentedTest {

    @Test
    fun runningTaskWithoutWorkIsMarkedFailed() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as OpenConvertApplication
        val taskId = "recovery-orphan-${UUID.randomUUID()}"
        try {
            app.historyRepository.save(
                ConversionTask(
                    id = taskId,
                    sourceUri = "content://openconvert/recovery-src",
                    sourceName = "recovery-src.jpg",
                    sourceFormat = FileFormat.JPG,
                    targetFormat = FileFormat.PNG,
                    status = ConversionStatus.RUNNING,
                    progress = 40,
                ),
            )

            val orphans = app.recoverOrphans()
            assertTrue(orphans.any { it.id == taskId })

            val stored = app.historyRepository.get(taskId)
            assertNotNull(stored)
            assertEquals(ConversionStatus.FAILED, stored!!.status)
            assertEquals(ConversionRecovery.ORPHAN_MESSAGE, stored.errorMessage)
            assertNotNull(stored.completedAt)
        } finally {
            app.historyRepository.get(taskId)?.let { app.historyRepository.delete(it) }
        }
    }

    @Test
    fun pendingTaskWithoutWorkIsMarkedFailed() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as OpenConvertApplication
        val taskId = "recovery-pending-${UUID.randomUUID()}"
        try {
            app.historyRepository.save(
                ConversionTask(
                    id = taskId,
                    sourceUri = "content://openconvert/recovery-pending",
                    sourceName = "recovery-pending.jpg",
                    sourceFormat = FileFormat.JPG,
                    targetFormat = FileFormat.WEBP,
                    status = ConversionStatus.PENDING,
                ),
            )
            app.recoverOrphans()
            val stored = requireNotNull(app.historyRepository.get(taskId))
            assertEquals(ConversionStatus.FAILED, stored.status)
            assertEquals(ConversionRecovery.ORPHAN_MESSAGE, stored.errorMessage)
        } finally {
            app.historyRepository.get(taskId)?.let { app.historyRepository.delete(it) }
        }
    }
}
