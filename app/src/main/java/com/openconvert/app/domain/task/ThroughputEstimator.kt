package com.openconvert.app.domain.task

/**
 * 单任务卡片的速度与剩余时间（计划书 §7.1）。
 *
 * ```
 * movie.mkv
 * MKV → MP4
 * ████████░░ 82%
 * 速度：41 MB/s
 * 剩余：1m 23s
 * 引擎：MediaCodec
 * ```
 *
 * 数据来源是进度百分比随时间的变化率，而不是真实字节计数——各引擎
 * （libvips / MediaCodec / FFmpeg / PdfBox）汇报进度的粒度差异很大，
 * 统一用「已完成比例 × 输入体积」估算，是唯一能对所有引擎一致工作的口径。
 * 因此这是**估算值**，不是精确吞吐基准。
 */
data class ThroughputEstimate(
    /** 每秒处理的字节数；无法估算时为 null。 */
    val bytesPerSecond: Long?,
    /** 预计剩余毫秒；无法估算或已完成时为 null。 */
    val remainingMillis: Long?,
) {
    companion object {
        val UNKNOWN = ThroughputEstimate(null, null)
    }
}

/**
 * 进度采样点。任务中心持有每个任务最近一次采样，用两点差分算速率。
 */
data class ProgressSample(
    val progressPercent: Int,
    val atMillis: Long,
    val bytesProcessed: Long = 0L,
)

object ThroughputEstimator {

    /** 低于此进度增量不更新估算，避免除以极小值得到荒谬速率。 */
    private const val MIN_PROGRESS_DELTA = 1

    /** 低于此时间间隔同样跳过（毫秒）。 */
    private const val MIN_ELAPSED_MS = 300L

    /**
     * 用两个采样点估算速度与剩余时间。
     *
     * @param inputBytes 输入文件体积；为 0 时无法给出速度（但仍可给剩余时间）。
     */
    fun estimate(
        previous: ProgressSample,
        current: ProgressSample,
        inputBytes: Long,
    ): ThroughputEstimate {
        val elapsed = current.atMillis - previous.atMillis
        if (elapsed < MIN_ELAPSED_MS) return ThroughputEstimate.UNKNOWN

        val byteDelta = current.bytesProcessed - previous.bytesProcessed
        if (byteDelta > 0L && current.bytesProcessed > 0L) {
            val bytesPerSecond = (byteDelta.toDouble() / elapsed.toDouble() * 1000.0).toLong().coerceAtLeast(0L)
            val remainingBytes = (inputBytes - current.bytesProcessed).coerceAtLeast(0L)
            val remainingMs = if (bytesPerSecond > 0L && remainingBytes > 0L) {
                remainingBytes * 1000L / bytesPerSecond
            } else if (remainingBytes == 0L) {
                null
            } else {
                null
            }
            return ThroughputEstimate(bytesPerSecond, remainingMs)
        }

        val progressDelta = current.progressPercent - previous.progressPercent
        if (progressDelta < MIN_PROGRESS_DELTA) {
            return ThroughputEstimate.UNKNOWN
        }

        // 每毫秒推进的百分点。
        val percentPerMs = progressDelta.toDouble() / elapsed.toDouble()
        if (percentPerMs <= 0.0) return ThroughputEstimate.UNKNOWN

        val remainingPercent = (100 - current.progressPercent).coerceAtLeast(0)
        val remainingMs = if (remainingPercent == 0) {
            null
        } else {
            (remainingPercent / percentPerMs).toLong().coerceAtLeast(0L)
        }

        val bytesPerSecond = if (inputBytes > 0L) {
            // 已处理字节 ≈ 输入体积 × 进度比例。
            val bytesDelta = inputBytes.toDouble() * progressDelta / 100.0
            (bytesDelta / elapsed.toDouble() * 1000.0).toLong().coerceAtLeast(0L)
        } else {
            null
        }

        return ThroughputEstimate(bytesPerSecond, remainingMs)
    }

    /** "41 MB/s" / "820 KB/s"。 */
    fun formatSpeed(bytesPerSecond: Long?): String? {
        val bps = bytesPerSecond ?: return null
        if (bps <= 0L) return null
        val mb = 1024.0 * 1024
        val kb = 1024.0
        return when {
            bps >= mb -> String.format("%.1f MB/s", bps / mb)
            bps >= kb -> String.format("%.0f KB/s", bps / kb)
            else -> "$bps B/s"
        }
    }

    /** "1m 23s" / "12s" / "1h 04m"。 */
    fun formatRemaining(millis: Long?): String? {
        val ms = millis ?: return null
        if (ms <= 0L) return null
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
