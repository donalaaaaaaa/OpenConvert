package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.benchmark.BenchmarkCollector
import com.openconvert.app.domain.benchmark.BenchmarkRecord
import com.openconvert.app.domain.benchmark.measurePeakMemory
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.suggestedOutputName
import com.openconvert.app.domain.planner.ConversionPlan
import com.openconvert.app.domain.planner.ConversionPlanner
import com.openconvert.app.domain.planner.DeviceHardwareFacts
import com.openconvert.app.domain.planner.PlanRejection
import com.openconvert.app.domain.planner.PlanRejectionMessages
import com.openconvert.app.domain.planner.PlanRequest
import com.openconvert.app.domain.planner.PlanResult
import com.openconvert.app.domain.planner.RuntimeFacts
import com.openconvert.app.domain.work.StorageGuard
import kotlinx.coroutines.CancellationException

sealed interface ExecutionResult {
    data class Success(
        val outputUri: String?,
        val outputUris: List<String>,
        val outputSize: Long,
        val outputName: String?,
        val actualEngine: EngineType? = null,
    ) : ExecutionResult

    data class Failure(val message: String) : ExecutionResult
    data object Cancelled : ExecutionResult
}

class ConversionExecutor(private val context: Context) {
    private val resolver = context.contentResolver
    private val mediaConverter = MediaConverter(context)
    private val registry = ConverterRegistry(
        listOf(
            ImageConverter(resolver),
            mediaConverter,
            OfficeConverter(context),
        ),
    )

    /**
     * 写一条 §11.1 指标。失败任务也记——排查性能回归时"哪些组合会失败"
     * 与"多快"同等重要。采集本身绝不能影响转换结果，故整体吞掉异常。
     */
    private fun recordBenchmark(
        collector: BenchmarkCollector,
        task: ConversionTask,
        result: ExecutionResult,
        engine: com.openconvert.app.domain.engine.EngineType?,
        streamCopy: Boolean,
        hardware: Boolean,
        startedAt: Long,
        peakMemory: Long,
    ) {
        runCatching {
            val success = result as? ExecutionResult.Success
            collector.record(
                BenchmarkRecord(
                    taskId = task.id,
                    inputFormat = task.sourceFormat,
                    outputFormat = task.targetFormat,
                    inputBytes = task.fileSize,
                    outputBytes = success?.outputSize ?: 0L,
                    elapsedMillis = System.currentTimeMillis() - startedAt,
                    engine = success?.actualEngine ?: engine,
                    streamCopy = streamCopy,
                    hardwareEncode = hardware,
                    peakMemoryBytes = peakMemory,
                    succeeded = success != null,
                ),
            )
        }
    }

    suspend fun execute(
        task: ConversionTask,
        onProgress: suspend (Int) -> Unit,
    ): ExecutionResult {
        // §11.1 指标采集：引擎/流拷贝来自 Planner，耗时与峰值内存在这里量。
        var plannedEngine: com.openconvert.app.domain.engine.EngineType? = null
        var plannedStreamCopy = false
        var plannedHardware = false
        var plannedConversion: ConversionPlan? = null
        var plannedCodecs: StreamCodecs? = null
        val collector = BenchmarkCollector(context)
        val startedAt = System.currentTimeMillis()
        val baselineMemory = collector.sampleMemory()

        fun finishSingle(result: ExecutionResult, peakMemory: Long = baselineMemory): ExecutionResult {
            recordBenchmark(
                collector = collector,
                task = task,
                result = result,
                engine = plannedEngine,
                streamCopy = plannedStreamCopy,
                hardware = plannedHardware,
                startedAt = startedAt,
                peakMemory = peakMemory,
            )
            return result
        }

        // SINGLE 流程走 Planner：能力校验 + 空间预检 + 引擎/并发决策一次完成，
        // 拒绝时返回带具体数字的原因（计划书 §5、§7.3）。
        // 工具类 kind（PDF/归档/多文件）的输入形态不同，仍用 StorageGuard 直接预检。
        if (task.kind == ConversionKind.SINGLE) {
            if (task.sourceFormat.category in setOf(FileCategory.AUDIO, FileCategory.VIDEO)) {
                plannedCodecs = try {
                    mediaConverter.inspectForPlanning(task)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // 探测失败不等同于输入必坏；允许媒体引擎按未知编码走保守重编码路径。
                    null
                }
            }
            val planResult = ConversionPlanner.plan(
                PlanRequest(
                    input = task.sourceFormat,
                    target = task.targetFormat,
                    inputBytes = task.fileSize,
                    quality = task.quality,
                    resolution = task.resolution,
                    codecs = plannedCodecs,
                    hardware = DeviceHardwareFacts(),
                    runtime = RuntimeFacts(usableScratchBytes = context.cacheDir.usableSpace),
                    copiesInputToCache = true,
                ),
            )
            when (planResult) {
                is PlanResult.Rejected -> return finishSingle(
                    ExecutionResult.Failure(
                        PlanRejectionMessages.describe(planResult.rejection),
                    ),
                )
                is PlanResult.Ready -> {
                    plannedConversion = planResult.plan
                    plannedEngine = planResult.plan.primaryEngine
                    plannedStreamCopy = planResult.plan.isStreamCopy
                    plannedHardware = planResult.plan.encodeMode in setOf(
                        EncodeMode.HARDWARE_H264,
                        EncodeMode.LITR_VP8,
                    )
                    android.util.Log.i(
                        "OpenConvert",
                        "planner task=${task.id} engine=${planResult.plan.primaryEngine} " +
                            "fallback=${planResult.plan.fallbackEngine} mode=${planResult.plan.encodeMode} " +
                            "streamCopy=${planResult.plan.isStreamCopy} slot=${planResult.plan.concurrency} " +
                            "reason=${planResult.plan.reason}",
                    )
                }
            }
        } else {
            val required = StorageGuard.requiredScratchBytes(
                inputBytes = task.fileSize,
                copiesInput = false,
            )
            if (!StorageGuard.hasEnoughSpace(context.cacheDir.usableSpace, required)) {
                return ExecutionResult.Failure(
                    PlanRejectionMessages.describe(
                        PlanRejection.InsufficientSpace(
                            requiredBytes = required,
                            availableBytes = context.cacheDir.usableSpace,
                        ),
                    ),
                )
            }
        }

        return when (task.kind) {
            ConversionKind.SINGLE -> {
                val outputUri = task.outputUri?.let { Uri.parse(it) }
                    ?: createBatchOutput(task)
                    ?: return finishSingle(ExecutionResult.Failure("没有选择输出文件"))
                val withOutput = task.copy(outputUri = outputUri.toString())
                val measured = measurePeakMemory(
                    initialBytes = baselineMemory,
                    sample = collector::sampleMemory,
                ) {
                    registry.convert(
                        task = withOutput,
                        plan = plannedConversion,
                        codecs = plannedCodecs,
                    )
                }
                finishSingle(
                    mapResult(
                        measured.value,
                        fallbackName = task.outputName,
                    ),
                    peakMemory = measured.peakBytes,
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

            ConversionKind.PDF_COMPRESS -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val res = runCatching {
                    PdfCompressConverter(context, onProgress).compress(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        customDpi = task.payload.compressDpi,
                        customQuality = task.payload.compressQuality,
                    )
                }.getOrElse { return ExecutionResult.Failure(it.message ?: "PDF 压缩失败") }
                ExecutionResult.Success(
                    outputUri = outputUri.toString(),
                    outputUris = listOf(outputUri.toString()),
                    outputSize = res.outputSizeBytes,
                    outputName = task.outputName ?: res.message,
                    actualEngine = EngineType.PDFBOX,
                )
            }

            ConversionKind.PDF_PAGE_MANAGER -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val pageItems = task.payload.pages.mapIndexed { idx, orig ->
                    com.openconvert.app.domain.pdf.PdfPageItem(
                        id = "page_$idx",
                        originalPageIndex = orig,
                        rotationDegrees = task.payload.rotateDegrees,
                    )
                }
                val size = runCatching {
                    com.openconvert.app.domain.pdf.PdfPageManager(context).exportPdf(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        pageLayout = pageItems,
                        onProgress = onProgress,
                    )
                }.getOrElse { return ExecutionResult.Failure(it.message ?: "页面导出失败") }
                ExecutionResult.Success(
                    outputUri = outputUri.toString(),
                    outputUris = listOf(outputUri.toString()),
                    outputSize = size,
                    outputName = task.outputName,
                    actualEngine = EngineType.PDFBOX,
                )
            }

            ConversionKind.PDF_SECURITY -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val size = runCatching {
                    if (task.payload.isEncrypt) {
                        PdfSecurityConverter(context, onProgress).encrypt(
                            inputUri = inputUri,
                            outputUri = outputUri,
                            userPassword = task.payload.password,
                        )
                    } else {
                        PdfSecurityConverter(context, onProgress).decrypt(
                            inputUri = inputUri,
                            outputUri = outputUri,
                            password = task.payload.password,
                        )
                    }
                }.getOrElse { return ExecutionResult.Failure(it.message ?: "PDF 安全处理失败") }
                ExecutionResult.Success(
                    outputUri = outputUri.toString(),
                    outputUris = listOf(outputUri.toString()),
                    outputSize = size,
                    outputName = task.outputName,
                    actualEngine = EngineType.PDFBOX,
                )
            }

            ConversionKind.PDF_CROP -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val size = runCatching {
                    PdfCropConverter(context, onProgress).crop(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        margins = PdfCropMargins(
                            leftPt = task.payload.cropMarginsLeft,
                            topPt = task.payload.cropMarginsTop,
                            rightPt = task.payload.cropMarginsRight,
                            bottomPt = task.payload.cropMarginsBottom,
                        ),
                        targetPages = task.payload.pages.takeIf { it.isNotEmpty() }?.toSet(),
                    )
                }.getOrElse { return ExecutionResult.Failure(it.message ?: "PDF 裁剪失败") }
                ExecutionResult.Success(
                    outputUri = outputUri.toString(),
                    outputUris = listOf(outputUri.toString()),
                    outputSize = size,
                    outputName = task.outputName,
                    actualEngine = EngineType.PDFBOX,
                )
            }

            ConversionKind.PDF_METADATA -> {
                val inputUri = sourceUris(task).firstOrNull()
                    ?: return ExecutionResult.Failure("没有选择 PDF")
                val outputUri = task.outputUri?.let(Uri::parse)
                    ?: return ExecutionResult.Failure("没有选择输出文件")
                val size = runCatching {
                    com.openconvert.app.domain.pdf.PdfMetadataManager(context).updateMetadata(
                        inputUri = inputUri,
                        outputUri = outputUri,
                        title = task.payload.metadataTitle,
                        author = task.payload.metadataAuthor,
                        subject = task.payload.metadataSubject,
                        keywords = task.payload.metadataKeywords,
                    )
                }.getOrElse { return ExecutionResult.Failure(it.message ?: "PDF 元数据修改失败") }
                ExecutionResult.Success(
                    outputUri = outputUri.toString(),
                    outputUris = listOf(outputUri.toString()),
                    outputSize = size,
                    outputName = task.outputName,
                    actualEngine = EngineType.PDFBOX,
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
            actualEngine = result.actualEngine,
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
            actualEngine = EngineType.PDFBOX,
        )
        is PdfBatchResult.Failure -> ExecutionResult.Failure(result.message)
        PdfBatchResult.Cancelled -> ExecutionResult.Cancelled
    }
}
