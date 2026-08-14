package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.work.StorageGuard

sealed interface ExecutionResult {
    data class Success(
        val outputUri: String?,
        val outputUris: List<String>,
        val outputSize: Long,
        val outputName: String?,
    ) : ExecutionResult

    data class Failure(val message: String) : ExecutionResult
    data object Cancelled : ExecutionResult
}

class ConversionExecutor(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun execute(
        task: ConversionTask,
        onProgress: suspend (Int) -> Unit,
    ): ExecutionResult {
        val required = StorageGuard.requiredScratchBytes(
            inputBytes = task.fileSize,
            copiesInput = task.kind == ConversionKind.SINGLE,
        )
        if (!StorageGuard.hasEnoughSpace(context.cacheDir.usableSpace, required)) {
            return ExecutionResult.Failure(StorageGuard.INSUFFICIENT_SPACE)
        }

        return when (task.kind) {
            ConversionKind.SINGLE -> mapResult(
                ConversionRouter(
                    listOf(
                        ImageConverter(resolver, onProgress),
                        MediaConverter(context, onProgress),
                    ),
                ).convert(task),
                fallbackName = task.outputName,
            )

            ConversionKind.IMAGES_TO_PDF -> {
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                mapResult(
                    ImagesToPdfConverter(resolver, onProgress).convert(sourceUris(task), outputUri),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.PDF_TO_IMAGES -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val treeUri = task.payload.outputTreeUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择保存文件夹")
                val pages = task.payload.pages.ifEmpty {
                    val count = task.payload.pageRanges.substringAfterLast('-').toIntOrNull() ?: 0
                    flattenPdfPages(parsePdfPageRanges(task.payload.pageRanges, count).getOrElse {
                        return ExecutionResult.Failure(it.message ?: "页码格式不正确")
                    })
                }
                mapBatch(
                    PdfToImagesConverter(context, onProgress).convert(
                        inputUri = inputUri,
                        outputTreeUri = treeUri,
                        pages = pages,
                        targetFormat = task.targetFormat,
                        sourceName = task.sourceName,
                    ),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.PDF_MERGE -> {
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                mapResult(
                    PdfMergeConverter(context, onProgress).convert(sourceUris(task), outputUri),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.PDF_SPLIT -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val treeUri = task.payload.outputTreeUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择保存文件夹")
                if (task.payload.pageRanges.isBlank()) {
                    return ExecutionResult.Failure("没有输入拆分页码")
                }
                val ranges = parsePdfPageRanges(task.payload.pageRanges, 100_000).getOrElse {
                    return ExecutionResult.Failure(it.message ?: "页码格式不正确")
                }
                mapBatch(
                    PdfSplitConverter(context, onProgress).convert(
                        inputUri,
                        treeUri,
                        ranges,
                        task.sourceName,
                    ),
                    fallbackName = task.outputName,
                )
            }
        }
    }

    private fun sourceUris(task: ConversionTask): List<Uri> =
        task.payload.sourceUris.ifEmpty { listOf(task.sourceUri) }.map(Uri::parse)

    private fun mapResult(result: ConversionResult, fallbackName: String?): ExecutionResult = when (result) {
        is ConversionResult.Success -> ExecutionResult.Success(
            outputUri = result.outputUri,
            outputUris = listOf(result.outputUri),
            outputSize = result.outputSize,
            outputName = fallbackName,
        )
        is ConversionResult.Failure -> ExecutionResult.Failure(result.message)
        ConversionResult.Cancelled -> ExecutionResult.Cancelled
    }

    private fun mapBatch(result: PdfBatchResult, fallbackName: String?): ExecutionResult = when (result) {
        is PdfBatchResult.Success -> ExecutionResult.Success(
            outputUri = result.outputUris.firstOrNull(),
            outputUris = result.outputUris,
            outputSize = result.outputSize,
            outputName = fallbackName ?: "${result.outputUris.size} 个文件",
        )
        is PdfBatchResult.Failure -> ExecutionResult.Failure(result.message)
        PdfBatchResult.Cancelled -> ExecutionResult.Cancelled
    }
}
