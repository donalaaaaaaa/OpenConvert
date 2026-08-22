package com.openconvert.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.task.TaskBucket
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务中心 2.0（计划书 §七）在真 Room 上的端到端验收：
 * 任务写库 → ViewModel 分组 → 卡片模型可渲染。
 */
@RunWith(AndroidJUnit4::class)
class TaskCenterInstrumentedTest {

    private val app: OpenConvertApplication
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as OpenConvertApplication

    private fun task(
        status: ConversionStatus,
        progress: Int = 0,
        errorMessage: String? = null,
        outputSize: Long? = null,
        completedAt: Long? = null,
        actualEngine: EngineType? = null,
    ) = ConversionTask(
        id = "tc-${UUID.randomUUID()}",
        sourceUri = "content://test/src",
        sourceName = "movie.mkv",
        sourceFormat = FileFormat.MKV,
        targetFormat = FileFormat.MP4,
        status = status,
        progress = progress,
        fileSize = 100L * 1024 * 1024,
        outputSize = outputSize,
        errorMessage = errorMessage,
        createdAt = System.currentTimeMillis() - 13_000,
        completedAt = completedAt,
        actualEngine = actualEngine,
    )

    @Test
    fun runningTaskAppearsInRunningGroupWithProgress() = runBlocking {
        val vm = MainViewModel(app)
        val t = task(ConversionStatus.RUNNING, progress = 82)
        try {
            app.historyRepository.save(t)
            awaitCard(vm, t.id)

            val bucket = vm.taskGroups.value
                .firstOrNull { group -> group.tasks.any { it.id == t.id } }
                ?.bucket
            assertEquals(TaskBucket.RUNNING, bucket)

            val card = vm.taskCards.value[t.id]!!
            assertEquals("MKV → MP4", card.route)
            assertEquals(82, card.progressPercent)
        } finally {
            app.historyRepository.delete(t)
        }
    }

    @Test
    fun failedTaskCardCarriesAReadableReasonAndSuggestion() = runBlocking {
        val vm = MainViewModel(app)
        val t = task(
            ConversionStatus.FAILED,
            errorMessage = "存储空间不足，请清理后再试",
            completedAt = System.currentTimeMillis(),
        )
        try {
            app.historyRepository.save(t)
            awaitCard(vm, t.id)

            val card = vm.taskCards.value[t.id]!!
            val error = card.error
            assertNotNull("失败卡片必须带结构化错误", error)
            assertTrue(error!!.title.isNotBlank())
            assertNotNull("§7.3 要求给出下一步", error.suggestion)
        } finally {
            app.historyRepository.delete(t)
        }
    }

    @Test
    fun completedTaskShowsSizeSummaryAndElapsed() = runBlocking {
        val vm = MainViewModel(app)
        val t = task(
            ConversionStatus.COMPLETED,
            progress = 100,
            outputSize = 7L * 1024 * 1024,
            completedAt = System.currentTimeMillis(),
            actualEngine = EngineType.FFMPEG_KIT,
        )
        try {
            app.historyRepository.save(t)
            awaitCard(vm, t.id)

            val card = vm.taskCards.value[t.id]!!
            assertNotNull(card.sizeSummary)
            assertTrue(card.sizeSummary!!.contains("输入"))
            assertNotNull("应显示耗时", card.elapsedText)
            assertEquals("引擎 · FFmpegKit", card.engineText)
            // 完成后不再显示瞬时速度。
            assertEquals(null, card.speedText)
        } finally {
            app.historyRepository.delete(t)
        }
    }

    @Test
    fun everyGroupedTaskHasARenderableCard() = runBlocking {
        val vm = MainViewModel(app)
        val tasks = listOf(
            task(ConversionStatus.RUNNING, progress = 10),
            task(ConversionStatus.PENDING),
            task(ConversionStatus.FAILED, errorMessage = "转换失败"),
            task(ConversionStatus.COMPLETED, completedAt = System.currentTimeMillis()),
        )
        try {
            tasks.forEach { app.historyRepository.save(it) }
            awaitCard(vm, tasks.last().id)

            // 分组里出现的每个任务都必须能取到卡片，否则 UI 会渲染空行。
            vm.taskGroups.value.forEach { group ->
                group.tasks.forEach { t ->
                    assertNotNull(
                        "${group.bucket} 中的 ${t.id} 没有卡片",
                        vm.taskCards.value[t.id],
                    )
                }
            }
        } finally {
            tasks.forEach { app.historyRepository.delete(it) }
        }
    }

    private suspend fun awaitCard(vm: MainViewModel, taskId: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (vm.taskCards.value.containsKey(taskId)) return
            delay(100)
        }
        throw AssertionError("任务中心未在 ${timeoutMs}ms 内收到 $taskId")
    }
}
