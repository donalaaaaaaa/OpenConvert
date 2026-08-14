package com.openconvert.app.domain.model

enum class ConversionKind {
    SINGLE,
    IMAGES_TO_PDF,
    PDF_TO_IMAGES,
    PDF_MERGE,
    PDF_SPLIT,
    PDF_DELETE_PAGES,
    PDF_ROTATE_PAGES,
    BATCH,
    ARCHIVE_COMPRESS,
    ARCHIVE_EXTRACT,
}

data class ConversionPayload(
    val sourceUris: List<String> = emptyList(),
    val sourceNames: List<String> = emptyList(),
    val pageRanges: String = "",
    val pages: List<Int> = emptyList(),
    val outputTreeUri: String? = null,
    val outputUris: List<String> = emptyList(),
    val rotateDegrees: Int = 0,
    val batchId: String? = null,
    /** 图片高级：裁剪比例 "free"/"1:1"/"4:3"/"3:2"/"16:9"/"9:16" */
    val cropAspect: String = "free",
    /** 图片高级：翻转 0=无 1=水平 2=垂直 */
    val flip: Int = 0,
    /** 图片高级：剥离 EXIF/GPS 等全部元数据 */
    val stripMetadata: Boolean = false,
)
