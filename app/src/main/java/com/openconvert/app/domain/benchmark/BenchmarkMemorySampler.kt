package com.openconvert.app.domain.benchmark

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class MemoryMeasured<T>(
    val value: T,
    val peakBytes: Long,
)

/**
 * 在转换协程运行期间定期采样进程内存，而不是只比较开始/结束两个点。
 * 采样协程继承调用方作用域；转换结束或抛错时总会取消，不会泄漏后台任务。
 */
internal suspend fun <T> measurePeakMemory(
    initialBytes: Long,
    intervalMillis: Long = 100L,
    sample: () -> Long,
    block: suspend () -> T,
): MemoryMeasured<T> = coroutineScope {
    val peak = AtomicLong(initialBytes.coerceAtLeast(0L))
    fun observe() {
        val value = runCatching(sample).getOrDefault(0L)
        peak.updateAndGet { previous -> maxOf(previous, value) }
    }

    observe()
    val sampler = launch {
        while (isActive) {
            delay(intervalMillis)
            observe()
        }
    }
    try {
        val value = block()
        observe()
        MemoryMeasured(value, peak.get())
    } finally {
        sampler.cancelAndJoin()
    }
}
