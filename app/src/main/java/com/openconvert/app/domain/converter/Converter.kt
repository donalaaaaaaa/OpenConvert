package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat

interface Converter {
    fun supports(inputFormat: FileFormat, outputFormat: FileFormat): Boolean
    suspend fun convert(task: ConversionTask): ConversionResult
}

class ConversionRouter(private val converters: List<Converter>) {
    fun findConverter(inputFormat: FileFormat, outputFormat: FileFormat): Converter? =
        converters.firstOrNull { it.supports(inputFormat, outputFormat) }

    suspend fun convert(task: ConversionTask): ConversionResult {
        val converter = findConverter(task.sourceFormat, task.targetFormat)
            ?: return ConversionResult.Failure(
                "暂不支持 ${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            )
        return converter.convert(task)
    }
}

