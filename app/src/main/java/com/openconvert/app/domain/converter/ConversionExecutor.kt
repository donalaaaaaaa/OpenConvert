package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.suggestedOutputName
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
            ConversionKind.SINGLE -> {
                val outputUri = task.outputUri?.let { Uri.parse(it) }
                    ?: createBatchOutput(task)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val withOutput = task.copy(outputUri = outputUri.toString())
                mapResult(
                    ConversionRouter(
                        listOf(
                            ImageConverter(resolver, onProgress),
                            MediaConverter(context, onProgress),
                        ),
                    ).convert(withOutput),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.BATCH ->
                ExecutionResult.Failure("批量任务由 ConversionWorker 逐文件调度，不直接执行")

            ConversionKind.ARCHIVE_COMPRESS -> {
                val outputUri = task.outputUri?.let { Uri.parse(it) }
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val uris = sourceUris(task)
                val names = task.payload.sourceNames.ifEmpty {
                    uris.mapIndexed { index, _ ->
                        if (index == 0) task.sourceName else "文件${index + 1}"
                    }
                }
                mapResult(
                    ArchiveConverter(context, onProgress).compress(
                        inputUris = uris,
                        inputNames = names,
                        outputUri = outputUri,
                        targetFormat = task.targetFormat,
                    ),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.ARCHIVE_EXTRACT -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择压缩包")
                val treeUri = task.payload.outputTreeUri?.let { Uri.parse(it) }
                    ?: return ExecutionResult.Failure("没有选择解压目录")
                val directory = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    ?: return ExecutionResult.Failure("无法访问所选文件夹")
                mapResult(
                    ArchiveConverter(context, onProgress).extract(
                        inputUri = inputUri,
                        outputDirectory = directory,
                        sourceName = task.sourceName,
                    ),
                    fallbackName = task.outputName,
                )
            }

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

            ConversionKind.PDF_DELETE_PAGES -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                if (task.payload.pages.isEmpty()) {
                    return ExecutionResult.Failure("没有选择需要删除的页面")
                }
                mapResult(
                    PdfDeletePagesConverter(context, onProgress).convert(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        pagesToDelete = task.payload.pages,
                        sourceName = task.sourceName,
                    ),
                    fallbackName = task.outputName,
                )
            }

            ConversionKind.PDF_ROTATE_PAGES -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                mapResult(
                    PdfRotatePagesConverter(context, onProgress).convert(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        degrees = task.payload.rotateDegrees,
                        pages = task.payload.pages.ifEmpty { null },
                    ),
                    fallbackName = task.outputName,
                )
            }
        }
    }

    private fun sourceUris(task: ConversionTask): List<Uri> =
        task.payload.sourceUris.ifEmpty { listOf(task.sourceUri) }.map(Uri::parse)

    /** 批量任务：在输出目录里按建议文件名创建输出文档。 */
    private fun createBatchOutput(task: ConversionTask): Uri? {
        val treeUri = task.payload.outputTreeUri?.let(Uri::parse) ?: return null
        val directory = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: return null
        val name = task.outputName ?: suggestedOutputName(task.sourceName, task.targetFormat)
        return directory.createFile(task.targetFormat.mimeType, name)?.uri
    }

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
