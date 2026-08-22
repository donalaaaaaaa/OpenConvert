package com.openconvert.app.domain.model

/**
 * 转换进度。百分比始终有；字节数在流式路径上才有，供速度估算和进度条副标题用。
 */
data class ConversionProgress(
    val percent: Int,
    val bytesProcessed: Long = 0L,
    val bytesTotal: Long = 0L,
) {
    val coercedPercent: Int get() = percent.coerceIn(0, 100)

    companion object {
        fun percent(value: Int) = ConversionProgress(value.coerceIn(0, 100))

        fun bytes(processed: Long, total: Long): ConversionProgress {
            val percent = if (total > 0L) {
                ((processed * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }
            return ConversionProgress(percent, processed.coerceAtLeast(0L), total.coerceAtLeast(0L))
        }
    }
}
