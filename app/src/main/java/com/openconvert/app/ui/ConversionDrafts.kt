package com.openconvert.app.ui

import com.openconvert.app.data.saf.SelectedDocument
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.model.BatchJob
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.openconvert.app.domain.model.suggestedOutputName
import com.openconvert.app.domain.preset.Preset

data class ConversionDraft(
    val document: SelectedDocument,
    val targetFormat: FileFormat,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val rotateDegrees: Int = 0,
    val cropAspect: String = "free",
    val flip: Int = 0,
    val stripMetadata: Boolean = false,
    /** 已应用的预设 id；null = 手动设置（计划书 §八）。 */
    val presetId: String? = null,
    /** 预设的尺寸约束，转换时由 PresetSizing 换算成目标像素。 */
    val longestEdgePx: Int? = null,
    val fixedWidthPx: Int? = null,
    val fixedHeightPx: Int? = null,
) {
    val suggestedOutputName: String
        get() = suggestedOutputName(document.name, targetFormat)

    val engineAvailable: Boolean
        get() = FileCapabilityResolver.canConvertInEdition(document.format, targetFormat)
}

data class ImagesToPdfDraft(val documents: List<SelectedDocument>) {
    val suggestedOutputName: String
        get() = if (documents.size == 1) {
            suggestedOutputName(documents.first().name, FileFormat.PDF)
        } else {
            "OpenConvert_${documents.size}_images.pdf"
        }
}

data class PdfToImagesDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val targetFormat: FileFormat = FileFormat.PNG,
    val pageRanges: String = "",
)

data class PdfMergeDraft(val documents: List<SelectedDocument>) {
    val suggestedOutputName: String = "OpenConvert_merged_${documents.size}_files.pdf"
}

data class PdfSplitDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val pageRanges: String = "1-$pageCount",
)

data class PdfDeletePagesDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val selectedPages: Set<Int> = emptySet(),
) {
    val remaining: Int get() = (pageCount - selectedPages.size).coerceAtLeast(0)
}

data class PdfRotatePagesDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val degrees: Int = 90,
    val pageRanges: String = "", // 空 = 全部页面
)

data class PdfCompressDraft(
    val document: SelectedDocument,
    val preset: com.openconvert.app.domain.converter.PdfCompressPreset = com.openconvert.app.domain.converter.PdfCompressPreset.BALANCED,
)

data class PdfSecurityDraft(
    val document: SelectedDocument,
    val isEncrypt: Boolean = true,
    val password: String = "",
)

data class PdfCropDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val leftPt: Float = 20f,
    val topPt: Float = 20f,
    val rightPt: Float = 20f,
    val bottomPt: Float = 20f,
)

data class PdfMetadataDraft(
    val document: SelectedDocument,
    val metadata: com.openconvert.app.domain.pdf.PdfMetadataInfo = com.openconvert.app.domain.pdf.PdfMetadataInfo(),
)

data class PdfWatermarkDraft(
    val document: SelectedDocument,
    val pageCount: Int,
    val text: String = "OpenConvert",
    val opacity: Float = 0.18f,
    val position: com.openconvert.app.domain.converter.PdfWatermarkPosition =
        com.openconvert.app.domain.converter.PdfWatermarkPosition.DIAGONAL,
)

data class PdfPageManagerDraft(
    val document: SelectedDocument,
    val pages: List<com.openconvert.app.domain.pdf.PdfPageItem> = emptyList(),
)

data class ArchiveCompressDraft(
    val documents: List<SelectedDocument>,
    val targetFormat: FileFormat,
) {
    val suggestedOutputName: String
        get() = when (targetFormat) {
            FileFormat.ZIP -> "OpenConvert_${documents.size}_files.zip"
            FileFormat.TAR -> "OpenConvert_${documents.size}_files.tar"
            FileFormat.GZIP -> "${documents.first().name.substringBeforeLast('.')}.gz"
            FileFormat.BZIP2 -> "${documents.first().name.substringBeforeLast('.')}.bz2"
            else -> "OpenConvert_archive.${targetFormat.preferredExtension}"
        }

    val singleFileOnly: Boolean get() = targetFormat in setOf(FileFormat.GZIP, FileFormat.BZIP2)
}

data class ArchiveExtractDraft(
    val document: SelectedDocument,
) {
    val suggestedFolderName: String
        get() = document.name.substringBeforeLast('.', missingDelimiterValue = "OpenConvert")
}

data class BatchDraft(
    val documents: List<SelectedDocument>,
    val targetFormat: FileFormat,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    /** 已应用的预设（§8.3 批量应用）；null = 手动设置。 */
    val presetId: String? = null,
    val longestEdgePx: Int? = null,
    val fixedWidthPx: Int? = null,
    val fixedHeightPx: Int? = null,
    val cropAspect: String = "free",
    val stripMetadata: Boolean = false,
) {
    val commonFormats: List<FileFormat>
        get() {
            val categories = documents.map { it.format.category }.distinct()
            if (categories.size != 1) return emptyList()
            return documents
                .map { FileCapabilityResolver.targetsForEdition(it.format).toSet() }
                .reduce { acc, next -> acc.intersect(next) }
                .sortedBy { it.displayName }
        }

    val engineAvailable: Boolean
        get() = targetFormat in commonFormats
}

/**
 * 批量页只展示与输入类别一致、且所有输入都可到达其目标格式的预设。
 * 视频本身也能提取 MP3；若只按目标格式筛选，会把“音频 MP3”预设错放到视频批量页。
 */
internal fun availableBatchPresets(
    sourceFormats: List<FileFormat>,
    commonFormats: Collection<FileFormat>,
    presets: List<com.openconvert.app.domain.preset.Preset>,
): List<com.openconvert.app.domain.preset.Preset> {
    val categories = sourceFormats.map { it.category }.distinct()
    if (categories.size != 1) return emptyList()
    val category = categories.single()
    return presets.filter { preset ->
        preset.category == category && preset.targetFormat in commonFormats
    }
}

sealed interface ConversionUiState {
    data object Configuring : ConversionUiState
    data class Running(val task: ConversionTask) : ConversionUiState
    data class Completed(
        val task: ConversionTask,
        val outputName: String,
        val outputUris: List<String> = listOfNotNull(task.outputUri),
    ) : ConversionUiState
    data class Failed(val task: ConversionTask, val message: String) : ConversionUiState
}

sealed interface BatchUiState {
    data object Idle : BatchUiState
    data class Configuring(val draft: BatchDraft) : BatchUiState
    data class Running(
        val job: BatchJob,
        val tasks: List<ConversionTask>,
    ) : BatchUiState
    data class Completed(val job: BatchJob, val tasks: List<ConversionTask>) : BatchUiState
}
