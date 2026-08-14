package com.openconvert.app.domain.converter

/**
 * 图片高级编辑的纯计算逻辑（可单测，不依赖 Android）。
 */
object ImageEditMath {

    /** 解析裁剪比例："1:1" → (1f, 1f)，"16:9" → (16f, 9f)；free/无效 → null。 */
    fun parseAspectRatio(value: String): Pair<Float, Float>? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "free" || trimmed.isBlank()) return null
        val parts = trimmed.split(':')
        if (parts.size != 2) return null
        val width = parts[0].toFloatOrNull() ?: return null
        val height = parts[1].toFloatOrNull() ?: return null
        if (width <= 0f || height <= 0f) return null
        return width to height
    }

    /** cover-crop 目标尺寸：保持原图比例的前提下填充目标比例。 */
    fun coverCropSize(width: Int, height: Int, aspect: Pair<Float, Float>): Pair<Int, Int> {
        val (aw, ah) = aspect
        val targetRatio = aw / ah
        val sourceRatio = width.toFloat() / height
        return if (sourceRatio > targetRatio) {
            // 源图更宽：按高度裁剪
            val cropWidth = (height * targetRatio).toInt().coerceAtLeast(1)
            cropWidth.coerceAtMost(width) to height
        } else {
            // 源图更高：按宽度裁剪
            val cropHeight = (width / targetRatio).toInt().coerceAtLeast(1)
            width to cropHeight.coerceAtMost(height)
        }
    }

    /** 旋转角度 → JNI 编码：0/1/2/3 = 无/90/180/270 CW。 */
    fun rotateCode(degrees: Int): Int = when (degrees % 360) {
        90 -> 1
        180 -> 2
        270 -> 3
        else -> 0
    }
}
