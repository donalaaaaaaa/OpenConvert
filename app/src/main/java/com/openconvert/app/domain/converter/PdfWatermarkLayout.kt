package com.openconvert.app.domain.converter

enum class PdfWatermarkPosition(val label: String) {
    DIAGONAL("斜向居中"),
    CENTER("正中"),
    FOOTER("页脚");

    val labelRes: Int
        get() = when (this) {
            DIAGONAL -> com.openconvert.app.R.string.watermark_diagonal
            CENTER -> com.openconvert.app.R.string.watermark_center
            FOOTER -> com.openconvert.app.R.string.watermark_footer
        }
}

data class PdfWatermarkPlacement(
    val x: Float,
    val y: Float,
    val rotationDeg: Float,
    val fontSize: Float,
)

object PdfWatermarkLayout {
    fun place(
        pageWidth: Float,
        pageHeight: Float,
        position: PdfWatermarkPosition,
        textLength: Int,
    ): PdfWatermarkPlacement {
        val fontSize = when (position) {
            PdfWatermarkPosition.FOOTER -> 12f
            PdfWatermarkPosition.CENTER -> (pageWidth / (textLength.coerceAtLeast(4) * 0.55f)).coerceIn(14f, 48f)
            PdfWatermarkPosition.DIAGONAL -> (pageWidth / (textLength.coerceAtLeast(4) * 0.5f)).coerceIn(18f, 64f)
        }
        return when (position) {
            PdfWatermarkPosition.DIAGONAL -> PdfWatermarkPlacement(
                x = pageWidth * 0.18f,
                y = pageHeight * 0.28f,
                rotationDeg = 35f,
                fontSize = fontSize,
            )
            PdfWatermarkPosition.CENTER -> PdfWatermarkPlacement(
                x = pageWidth * 0.20f,
                y = pageHeight * 0.48f,
                rotationDeg = 0f,
                fontSize = fontSize,
            )
            PdfWatermarkPosition.FOOTER -> PdfWatermarkPlacement(
                x = pageWidth * 0.12f,
                y = pageHeight * 0.06f,
                rotationDeg = 0f,
                fontSize = fontSize,
            )
        }
    }

    fun parsePosition(raw: String): PdfWatermarkPosition =
        runCatching { PdfWatermarkPosition.valueOf(raw) }.getOrDefault(PdfWatermarkPosition.DIAGONAL)
}
