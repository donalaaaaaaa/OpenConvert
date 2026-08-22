package com.openconvert.app.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.work.ConversionRecovery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `am force-stop` 之后不重装、直接 instrument。
 *
 * WorkManager 的持久任务在进程被杀后通常会重新入队，因此：
 * - 若 work 还在：Room 必须仍是 PENDING/RUNNING（不能被误标失败）
 * - 若 work 没了：Room 必须被 recoverOrphans 收成 FAILED
 */
@RunWith(AndroidJUnit4::class)
class ForceStopVerifyTest {
    @Test
    fun forceStopLeavesRoomConsistentWithWorkManager() {
        runBlocking {
            val app = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext as OpenConvertApplication
            val stored = app.historyRepository.get(ForceStopLiveSeedTest.TASK_ID)
            assertNotNull("seed task missing — Gradle 可能卸包清空了数据", stored)

            val orphans = app.recoverOrphans()
            val after = app.historyRepository.get(ForceStopLiveSeedTest.TASK_ID)
            assertNotNull(after)
            val active = app.conversionScheduler.activeTaskIds()
            if (ForceStopLiveSeedTest.TASK_ID in active) {
                assertTrue(
                    "WM 仍持有任务时不得标失败: ${after!!.status}",
                    after.status == ConversionStatus.PENDING || after.status == ConversionStatus.RUNNING,
                )
                assertTrue(
                    "仍有 work 时不应出现在 orphan 列表",
                    orphans.none { it.id == ForceStopLiveSeedTest.TASK_ID },
                )
            } else {
                assertEquals(ConversionStatus.FAILED, after!!.status)
                assertEquals(ConversionRecovery.ORPHAN_MESSAGE, after.errorMessage)
            }
        }
    }
}
