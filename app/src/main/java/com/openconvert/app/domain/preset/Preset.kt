package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

/**
 * 转换预设（计划书 §八）。
 *
 * §8.1 的示例要求「最长边 1920」「1024×1024」这类具体尺寸约束，
 * 而 [ResolutionPreset] 只能表达百分比缩放，因此新增两个可选维度：
 *
 * - [longestEdgePx]：限制最长边像素（等比缩放，小于该值时不放大）
 * - [fixedWidthPx] / [fixedHeightPx]：固定输出尺寸（配合 [cropAspect] 裁切）
 *
 * 三者互斥优先级：固定尺寸 > 最长边 > [resolution] 百分比。
 */
data class Preset(
    val id: String,
    val category: FileCategory,
    val name: String,
    val description: String,
    val targetFormat: FileFormat,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val stripMetadata: Boolean = false,
    /** 最长边上限（像素）；null = 不限制。 */
    val longestEdgePx: Int? = null,
    /** 固定输出宽度（像素）；与 [fixedHeightPx] 同时存在才生效。 */
    val fixedWidthPx: Int? = null,
    val fixedHeightPx: Int? = null,
    /** 裁剪比例，取值同 ConversionPayload.cropAspect（"free"/"1:1"/…）。 */
    val cropAspect: String = "free",
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = true,
    val createdAt: Long = 0L,
) {
    /** 尺寸约束的可读摘要，供 UI 在预设卡片上显示。 */
    val sizeSummary: String?
        get() = when {
            fixedWidthPx != null && fixedHeightPx != null -> "${fixedWidthPx}×$fixedHeightPx"
            longestEdgePx != null -> "最长边 $longestEdgePx"
            resolution != ResolutionPreset.ORIGINAL -> resolution.label
            else -> null
        }

    /** 该预设是否会改变像素尺寸。 */
    val resizesOutput: Boolean
        get() = fixedWidthPx != null || longestEdgePx != null ||
            resolution != ResolutionPreset.ORIGINAL
}
