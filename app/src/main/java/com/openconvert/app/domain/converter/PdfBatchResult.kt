package com.openconvert.app.domain.converter

sealed interface PdfBatchResult {
    data class Success(val outputUris: List<String>, val outputSize: Long) : PdfBatchResult
    data class Failure(val message: String, val cause: Throwable? = null) : PdfBatchResult
    data object Cancelled : PdfBatchResult
}
