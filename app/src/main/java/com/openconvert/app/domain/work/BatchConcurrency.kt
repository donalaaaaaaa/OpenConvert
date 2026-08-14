package com.openconvert.app.domain.work

import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import java.util.concurrent.Semaphore

/**
 * 批量任务并发闸门（计划书 §九）：
 * - 图片/音频等小文件：最多 [IMAGE_CONCURRENCY] 个并行
 * - 视频：最多 [VIDEO_CONCURRENCY] 个并行（MediaCodec / FFmpeg 资源有限）
 * 避免内存爆炸、IO 竞争、手机过热。
 */
object BatchConcurrency {
    private const val IMAGE_CONCURRENCY = 3
    private const val VIDEO_CONCURRENCY = 2

    private val imageGate = Semaphore(IMAGE_CONCURRENCY)
    private val videoGate = Semaphore(VIDEO_CONCURRENCY)

    /** 是否为批量任务（batchId 非空）。单任务不受闸门限制。 */
    fun isBatchTask(task: ConversionTask): Boolean = !task.payload.batchId.isNullOrBlank()

    suspend fun <T> withPermit(task: ConversionTask, block: suspend () -> T): T {
        if (!isBatchTask(task)) return block()
        val gate = if (task.sourceFormat.category == FileCategory.VIDEO) videoGate else imageGate
        gate.acquire()
        try {
            return block()
        } finally {
            gate.release()
        }
    }
}
