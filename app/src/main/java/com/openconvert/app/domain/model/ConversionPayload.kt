package com.openconvert.app.domain.model

enum class ConversionKind {
    SINGLE,
    IMAGES_TO_PDF,
    PDF_TO_IMAGES,
    PDF_MERGE,
    PDF_SPLIT,
}

data class ConversionPayload(
    val sourceUris: List<String> = emptyList(),
    val pageRanges: String = "",
    val pages: List<Int> = emptyList(),
    val outputTreeUri: String? = null,
    val outputUris: List<String> = emptyList(),
)
