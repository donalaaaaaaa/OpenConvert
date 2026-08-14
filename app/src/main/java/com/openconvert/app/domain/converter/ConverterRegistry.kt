package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.ConversionGraph
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat

/**
 * 转换引擎统一注册表（计划书 §三十三）。
 * 任务只调用 [convert]，不感知底层引擎；能力由 [ConversionGraph] 声明。
 * 每次转换记录：主引擎 / 兜底引擎 / 原因 / 耗时（logcat + 返回值语义）。
 */
class ConverterRegistry(
    private val converters: List<Converter>,
    private val logger: (String) -> Unit = { android.util.Log.i("OpenConvert", it) },
) {
    private val index: Map<Pair<FileFormat, FileFormat>, Converter> = buildMap {
        converters.forEach { converter ->
            FileFormat.entries.forEach { input ->
                ConversionGraph.targetsFor(input).forEach { target ->
                    if (converter.supports(input, target)) put(input to target, converter)
                }
            }
        }
    }

    /** 该组合是否有引擎可处理。 */
    fun supports(input: FileFormat, target: FileFormat): Boolean =
        input to target in index

    fun engines(): List<Converter> = converters

    /** 统一转换入口：Success / Fallback / Failed / Cancelled 语义，带引擎与耗时日志。 */
    suspend fun convert(task: ConversionTask): ConversionResult {
        val startedAt = System.currentTimeMillis()
        val converter = index[task.sourceFormat to task.targetFormat]
            ?: return ConversionResult.Failure(
                "暂不支持 ${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            )
        logger(
            "engine=${converter.engineName()} input=${task.sourceFormat} target=${task.targetFormat} " +
                "task=${task.id} started",
        )
        val result = converter.convert(task)
        val elapsed = System.currentTimeMillis() - startedAt
        val status = when (result) {
            is ConversionResult.Success -> "Success"
            is ConversionResult.Failure -> "Failed"
            ConversionResult.Cancelled -> "Cancelled"
        }
        logger(
            "engine=${converter.engineName()} input=${task.sourceFormat} target=${task.targetFormat} " +
                "task=${task.id} result=$status elapsed=${elapsed}ms",
        )
        return result
    }

    private fun Converter.engineName(): String = when (this) {
        is ImageConverter -> "libvips/BitmapFactory"
        is MediaConverter -> "Media3/FFmpeg"
        else -> this::class.simpleName ?: "unknown"
    }
}
