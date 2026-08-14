package com.openconvert.app.domain.work

object StorageGuard {
    const val SAFETY_MARGIN_BYTES = 64L * 1024 * 1024
    const val INSUFFICIENT_SPACE = "存储空间不足，请清理后再试"

    fun requiredScratchBytes(inputBytes: Long, copiesInput: Boolean): Long {
        val safeInput = inputBytes.coerceAtLeast(0L)
        val outputBudget = maxOf(safeInput, SAFETY_MARGIN_BYTES)
        val inputBudget = if (copiesInput) safeInput else 0L
        return inputBudget + outputBudget + SAFETY_MARGIN_BYTES
    }

    fun hasEnoughSpace(usableBytes: Long, requiredBytes: Long): Boolean = usableBytes >= requiredBytes
}
