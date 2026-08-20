package com.openconvert.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.data.saf.SelectedDocument
import com.openconvert.app.data.saf.readSelectedDocument
import com.openconvert.app.domain.converter.flattenPdfPages
import com.openconvert.app.domain.converter.parsePdfPageRanges
import com.openconvert.app.domain.model.BatchJob
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.BatchSettings
import com.openconvert.app.domain.model.BatchSettingsCodec
import com.openconvert.app.domain.model.ConversionGraph
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.openconvert.app.domain.model.availableTargets
import com.openconvert.app.domain.model.canConvertLocallyTo
import com.openconvert.app.domain.model.suggestedOutputName
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        get() = document.format.canConvertLocallyTo(targetFormat)
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
) {
    val commonFormats: List<FileFormat>
        get() {
            val categories = documents.map { it.format.category }.distinct()
            if (categories.size != 1) return emptyList()
            return documents
                .map { it.format.availableTargets().toSet() }
                .reduce { acc, next -> acc.intersect(next) }
                .sortedBy { it.displayName }
        }

    val engineAvailable: Boolean
        get() = targetFormat in commonFormats
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenConvertApplication
    private val resolver = application.contentResolver
    private var trackedTaskId: String? = null
    private var observeJob: Job? = null

    private val _draft = MutableStateFlow<ConversionDraft?>(null)
    val draft: StateFlow<ConversionDraft?> = _draft.asStateFlow()

    private val _imagesToPdfDraft = MutableStateFlow<ImagesToPdfDraft?>(null)
    val imagesToPdfDraft: StateFlow<ImagesToPdfDraft?> = _imagesToPdfDraft.asStateFlow()

    private val _pdfToImagesDraft = MutableStateFlow<PdfToImagesDraft?>(null)
    val pdfToImagesDraft: StateFlow<PdfToImagesDraft?> = _pdfToImagesDraft.asStateFlow()

    private val _pdfMergeDraft = MutableStateFlow<PdfMergeDraft?>(null)
    val pdfMergeDraft: StateFlow<PdfMergeDraft?> = _pdfMergeDraft.asStateFlow()

    private val _pdfSplitDraft = MutableStateFlow<PdfSplitDraft?>(null)
    val pdfSplitDraft: StateFlow<PdfSplitDraft?> = _pdfSplitDraft.asStateFlow()

    private val _pdfDeleteDraft = MutableStateFlow<PdfDeletePagesDraft?>(null)
    val pdfDeleteDraft: StateFlow<PdfDeletePagesDraft?> = _pdfDeleteDraft.asStateFlow()

    private val _pdfRotateDraft = MutableStateFlow<PdfRotatePagesDraft?>(null)
    val pdfRotateDraft: StateFlow<PdfRotatePagesDraft?> = _pdfRotateDraft.asStateFlow()

    private val _pdfCompressDraft = MutableStateFlow<PdfCompressDraft?>(null)
    val pdfCompressDraft: StateFlow<PdfCompressDraft?> = _pdfCompressDraft.asStateFlow()

    private val _pdfSecurityDraft = MutableStateFlow<PdfSecurityDraft?>(null)
    val pdfSecurityDraft: StateFlow<PdfSecurityDraft?> = _pdfSecurityDraft.asStateFlow()

    private val _pdfCropDraft = MutableStateFlow<PdfCropDraft?>(null)
    val pdfCropDraft: StateFlow<PdfCropDraft?> = _pdfCropDraft.asStateFlow()

    private val _pdfMetadataDraft = MutableStateFlow<PdfMetadataDraft?>(null)
    val pdfMetadataDraft: StateFlow<PdfMetadataDraft?> = _pdfMetadataDraft.asStateFlow()

    private val _pdfPageManagerDraft = MutableStateFlow<PdfPageManagerDraft?>(null)
    val pdfPageManagerDraft: StateFlow<PdfPageManagerDraft?> = _pdfPageManagerDraft.asStateFlow()

    private val _archiveCompressDraft = MutableStateFlow<ArchiveCompressDraft?>(null)
    val archiveCompressDraft: StateFlow<ArchiveCompressDraft?> = _archiveCompressDraft.asStateFlow()

    private val _archiveExtractDraft = MutableStateFlow<ArchiveExtractDraft?>(null)
    val archiveExtractDraft: StateFlow<ArchiveExtractDraft?> = _archiveExtractDraft.asStateFlow()

    private val _batchDraft = MutableStateFlow<BatchDraft?>(null)
    val batchDraft: StateFlow<BatchDraft?> = _batchDraft.asStateFlow()

    private val _batchJobId = MutableStateFlow<String?>(null)
    val batchJobId: StateFlow<String?> = _batchJobId.asStateFlow()

    private val _batchUiState = MutableStateFlow<BatchUiState>(BatchUiState.Idle)
    val batchUiState: StateFlow<BatchUiState> = _batchUiState.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionUiState>(ConversionUiState.Configuring)
    val conversionState: StateFlow<ConversionUiState> = _conversionState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 首页 UI 2.0（计划书 §六）：用户选中的文件及其可执行能力。
     * 文件驱动 —— 先选文件，再由 FileCapabilityResolver 告诉用户能做什么。
     */
    private val _pickedFile = MutableStateFlow<SelectedDocument?>(null)
    val pickedFile: StateFlow<SelectedDocument?> = _pickedFile.asStateFlow()

    private val _pickedCapabilities =
        MutableStateFlow<com.openconvert.app.domain.capability.FileCapabilities?>(null)
    val pickedCapabilities: StateFlow<com.openconvert.app.domain.capability.FileCapabilities?> =
        _pickedCapabilities.asStateFlow()

    val deviceProfile = com.openconvert.app.domain.device.DeviceCapabilities.getHardwareProfile()

    private val _cacheStats = MutableStateFlow<com.openconvert.app.domain.cache.CacheStats?>(null)
    val cacheStats: StateFlow<com.openconvert.app.domain.cache.CacheStats?> = _cacheStats.asStateFlow()

    val history = app.historyRepository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val imageQualityPreference = app.userPreferences.imageQuality
    val videoQualityPreference = app.userPreferences.videoQuality

    /**
     * 任务中心 2.0（计划书 §七）。速度追踪**不持久化**：瞬时信息存 Room
     * 需要一次迁移，而重启后看到几秒前的速率没有价值。
     */
    private val throughputTracker = com.openconvert.app.domain.task.ThroughputTracker()

    private val _taskCards =
        MutableStateFlow<Map<String, com.openconvert.app.domain.task.TaskCardModel>>(emptyMap())
    val taskCards: StateFlow<Map<String, com.openconvert.app.domain.task.TaskCardModel>> =
        _taskCards.asStateFlow()

    private val _taskGroups =
        MutableStateFlow<List<com.openconvert.app.domain.task.TaskGroup>>(emptyList())
    val taskGroups: StateFlow<List<com.openconvert.app.domain.task.TaskGroup>> =
        _taskGroups.asStateFlow()

    /** 预设列表（计划书 §八）。内置 + 用户自定义，均来自 Room。 */
    val presets = app.presetStore.presets.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** 当前转换草稿已应用的预设 id；null = 手动设置。 */
    private val _appliedPresetId = MutableStateFlow<String?>(null)
    val appliedPresetId: StateFlow<String?> = _appliedPresetId.asStateFlow()

    init {
        viewModelScope.launch {
            app.historyRepository.findActive().firstOrNull()?.let(::trackTask)
        }
        // 任务中心：history 变化即重算分组与卡片（含速度采样）。
        viewModelScope.launch {
            app.historyRepository.history.collect { tasks ->
                refreshTaskCenter(tasks)
            }
        }
    }

    /**
     * 重算任务中心状态。暂停判定需要批量任务状态——批量暂停时子任务保持
     * PENDING 以便恢复，只看任务本身会误报「等待中」。
     */
    private suspend fun refreshTaskCenter(tasks: List<ConversionTask>) {
        val pausedBatchIds = runCatching {
            app.database.batchJobDao().observeAll().first()
                .filter { it.status == BatchJobStatus.PAUSED.name }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())

        val estimates = throughputTracker.updateAll(tasks)
        _taskCards.value = tasks.associate { task ->
            task.id to com.openconvert.app.domain.task.TaskCardFactory.create(
                task = task,
                estimate = estimates[task.id]
                    ?: com.openconvert.app.domain.task.ThroughputEstimate.UNKNOWN,
            )
        }
        _taskGroups.value = com.openconvert.app.domain.task.TaskCenterGrouping.group(
            tasks = tasks,
            pausedBatchIds = pausedBatchIds,
        )
    }

    /**
     * 首页 UI 2.0 的文件驱动入口（计划书 §6.3）：只读取文件并解析能力，
     * **不**预设目标格式、不跳转。由用户在能力面板里挑选要做什么。
     *
     * 与 [onDocumentPicked] 的区别：后者是「工具驱动」遗留路径，会立刻
     * 选定第一个可用目标并进入转换配置页。
     */
    fun inspectFile(uri: Uri): Boolean {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val document = runCatching { resolver.readSelectedDocument(uri) }
            .getOrElse {
                _message.value = "无法读取所选文件"
                return false
            }

        val capabilities = com.openconvert.app.domain.capability.FileCapabilityResolver
            .resolve(document.format)
        if (!capabilities.hasAnything) {
            _message.value = if (document.format == FileFormat.UNKNOWN) {
                "暂不支持此文件格式"
            } else {
                "暂不支持 ${document.format.displayName}"
            }
            return false
        }

        _pickedFile.value = document
        _pickedCapabilities.value = capabilities
        return true
    }

    /** 从能力面板里选定一个转换目标，进入转换配置页。 */
    fun chooseConvertTarget(format: FileFormat): Boolean {
        val document = _pickedFile.value ?: return false
        if (!ConversionGraph.canConvert(document.format, format)) {
            _message.value = "暂不支持 ${document.format.displayName} → ${format.displayName}"
            return false
        }
        _draft.value = ConversionDraft(document, format, defaultQualityFor(document.format))
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun clearPickedFile() {
        _pickedFile.value = null
        _pickedCapabilities.value = null
    }

    fun onDocumentPicked(uri: Uri): Boolean {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val document = runCatching { resolver.readSelectedDocument(uri) }
            .getOrElse {
                _message.value = "无法读取所选文件"
                return false
            }

        if (document.format == FileFormat.UNKNOWN) {
            _message.value = "暂不支持此文件格式"
            return false
        }

        val firstTarget = document.format.availableTargets().firstOrNull()
        if (firstTarget == null) {
            // 有工具能力但没有一进一出的转换边（典型：PDF）——引导到对应工具页，
            // 而不是笼统地说「不支持」。
            _message.value = if (com.openconvert.app.domain.model.ConversionGraph.toolsFor(document.format).size > 1) {
                "${document.format.displayName} 请在工具页处理"
            } else {
                "暂不支持 ${document.format.displayName} 转换"
            }
            return false
        }

        _draft.value = ConversionDraft(document, firstTarget, defaultQualityFor(document.format))
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun selectTarget(format: FileFormat) {
        _draft.value = _draft.value?.copy(targetFormat = format)
    }

    fun selectQuality(quality: QualityPreset) {
        _draft.value = _draft.value?.copy(quality = quality)
    }

    fun selectResolution(resolution: ResolutionPreset) {
        _draft.value = _draft.value?.copy(resolution = resolution)
    }

    fun selectRotate(degrees: Int) {
        _draft.value = _draft.value?.copy(rotateDegrees = degrees)
    }

    fun selectCropAspect(aspect: String) {
        _draft.value = _draft.value?.copy(cropAspect = aspect)
    }

    fun selectFlip(flip: Int) {
        _draft.value = _draft.value?.copy(flip = flip)
    }

    fun selectStripMetadata(strip: Boolean) {
        _draft.value = _draft.value?.copy(stripMetadata = strip)
        _appliedPresetId.value = null
    }

    /**
     * 应用预设到当前草稿（计划书 §八）。
     *
     * 预设携带的尺寸约束（最长边 / 固定尺寸）在转换时由 PresetSizing 换算，
     * 这里只写入草稿能表达的部分：目标格式、质量、百分比分辨率、裁剪、去元数据。
     * 目标格式不被引擎支持时拒绝应用，避免草稿进入不可执行状态。
     */
    fun applyPreset(preset: com.openconvert.app.domain.preset.Preset): Boolean {
        val current = _draft.value ?: return false
        if (!ConversionGraph.canConvert(current.document.format, preset.targetFormat)) {
            _message.value =
                "「${preset.name}」的目标格式 ${preset.targetFormat.displayName} 不适用于 ${current.document.format.displayName}"
            return false
        }
        _draft.value = current.copy(
            targetFormat = preset.targetFormat,
            quality = preset.quality,
            resolution = preset.resolution,
            cropAspect = preset.cropAspect,
            stripMetadata = preset.stripMetadata,
            presetId = preset.id,
            longestEdgePx = preset.longestEdgePx,
            fixedWidthPx = preset.fixedWidthPx,
            fixedHeightPx = preset.fixedHeightPx,
        )
        _appliedPresetId.value = preset.id
        return true
    }

    /** 当前文件类别可用的预设。 */
    fun presetsForCurrentDraft(): List<com.openconvert.app.domain.preset.Preset> {
        val document = _draft.value?.document ?: return emptyList()
        return presets.value.filter { preset ->
            preset.category == document.format.category &&
                ConversionGraph.canConvert(document.format, preset.targetFormat)
        }
    }

    /** 保存当前草稿为自定义预设。 */
    fun saveDraftAsPreset(name: String) {
        val draft = _draft.value ?: return
        if (name.isBlank()) {
            _message.value = "请输入预设名称"
            return
        }
        viewModelScope.launch {
            val saved = app.presetStore.saveCustom(
                com.openconvert.app.domain.preset.Preset(
                    id = "",
                    category = draft.document.format.category,
                    name = name.trim(),
                    description = buildString {
                        append(draft.targetFormat.displayName)
                        append(" · ")
                        append(draft.quality.label)
                        if (draft.stripMetadata) append(" · 去元数据")
                    },
                    targetFormat = draft.targetFormat,
                    quality = draft.quality,
                    resolution = draft.resolution,
                    stripMetadata = draft.stripMetadata,
                    longestEdgePx = draft.longestEdgePx,
                    fixedWidthPx = draft.fixedWidthPx,
                    fixedHeightPx = draft.fixedHeightPx,
                    cropAspect = draft.cropAspect,
                    isBuiltIn = false,
                ),
            )
            _appliedPresetId.value = saved.id
            _message.value = "已保存预设「${saved.name}」"
        }
    }

    fun deletePreset(preset: com.openconvert.app.domain.preset.Preset) {
        viewModelScope.launch {
            val deleted = app.presetStore.deleteCustom(preset.id)
            _message.value = if (deleted) {
                "已删除预设「${preset.name}」"
            } else {
                "内置预设不可删除"
            }
        }
    }

    fun setDefaultPreset(preset: com.openconvert.app.domain.preset.Preset) {
        viewModelScope.launch {
            app.presetStore.setDefault(preset)
            _message.value = "「${preset.name}」已设为默认"
        }
    }

    fun onImagesPicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return false
        if (uniqueUris.size > MAX_PDF_IMAGES) {
            _message.value = "一次最多选择 $MAX_PDF_IMAGES 张图片"
            return false
        }

        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format.category == FileCategory.IMAGE }

        if (documents.isEmpty()) {
            _message.value = "没有读到可用的 JPG、PNG 或 WEBP 图片"
            return false
        }
        if (documents.size != uniqueUris.size) {
            _message.value = "已忽略无法读取或不支持的文件"
        }

        _imagesToPdfDraft.value = ImagesToPdfDraft(documents)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun movePdfImage(index: Int, direction: Int) {
        val current = _imagesToPdfDraft.value ?: return
        val target = index + direction
        if (index !in current.documents.indices || target !in current.documents.indices) return
        val reordered = current.documents.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        _imagesToPdfDraft.value = current.copy(documents = reordered)
    }

    fun removePdfImage(index: Int) {
        val current = _imagesToPdfDraft.value ?: return
        if (index !in current.documents.indices || current.documents.size == 1) return
        _imagesToPdfDraft.value = current.copy(
            documents = current.documents.filterIndexed { itemIndex, _ -> itemIndex != index },
        )
    }

    fun onPdfToImagesPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        _pdfToImagesDraft.value = PdfToImagesDraft(document, pageCount)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun selectPdfImageFormat(format: FileFormat) {
        if (format !in setOf(FileFormat.JPG, FileFormat.PNG)) return
        _pdfToImagesDraft.value = _pdfToImagesDraft.value?.copy(targetFormat = format)
    }

    fun updatePdfImageRanges(value: String) {
        _pdfToImagesDraft.value = _pdfToImagesDraft.value?.copy(pageRanges = value)
    }

    fun onPdfsToMergePicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.size < 2) {
            _message.value = "请至少选择 2 个 PDF"
            return false
        }
        if (uniqueUris.size > MAX_MERGE_PDFS) {
            _message.value = "一次最多合并 $MAX_MERGE_PDFS 个 PDF"
            return false
        }
        val documents = uniqueUris.mapNotNull(::readPdfDocument)
        if (documents.size != uniqueUris.size) {
            _message.value = "存在无法读取或不是 PDF 的文件"
            return false
        }
        _pdfMergeDraft.value = PdfMergeDraft(documents)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun moveMergePdf(index: Int, direction: Int) {
        val current = _pdfMergeDraft.value ?: return
        val target = index + direction
        if (index !in current.documents.indices || target !in current.documents.indices) return
        val reordered = current.documents.toMutableList().apply {
            val item = removeAt(index)
            add(target, item)
        }
        _pdfMergeDraft.value = current.copy(documents = reordered)
    }

    fun removeMergePdf(index: Int) {
        val current = _pdfMergeDraft.value ?: return
        if (index !in current.documents.indices || current.documents.size <= 2) return
        _pdfMergeDraft.value = current.copy(
            documents = current.documents.filterIndexed { itemIndex, _ -> itemIndex != index },
        )
    }

    fun onPdfToSplitPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        _pdfSplitDraft.value = PdfSplitDraft(document, pageCount)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun updatePdfSplitRanges(value: String) {
        _pdfSplitDraft.value = _pdfSplitDraft.value?.copy(pageRanges = value)
    }

    fun startConversion(outputUri: Uri) {
        val current = _draft.value ?: return
        if (!current.engineAvailable) {
            _message.value = "${current.document.format.displayName} → ${current.targetFormat.displayName} 引擎尚未接入"
            return
        }
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = current.document.format,
                targetFormat = current.targetFormat,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                quality = current.quality,
                resolution = current.resolution,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.SINGLE,
                payload = ConversionPayload(
                    rotateDegrees = current.rotateDegrees,
                    cropAspect = current.cropAspect,
                    flip = current.flip,
                    stripMetadata = current.stripMetadata,
                    // 预设的尺寸约束（§8.1）随任务下传，引擎侧由 PresetSizing 换算。
                    presetId = current.presetId,
                    longestEdgePx = current.longestEdgePx,
                    fixedWidthPx = current.fixedWidthPx,
                    fixedHeightPx = current.fixedHeightPx,
                ),
                outputName = current.suggestedOutputName,
            ),
        )
    }

    fun startImagesToPdf(outputUri: Uri) {
        val current = _imagesToPdfDraft.value ?: return
        if (current.documents.isEmpty()) return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.documents.first().uri.toString(),
                sourceName = if (current.documents.size == 1) current.documents.first().name else "${current.documents.size} 张图片",
                sourceFormat = current.documents.first().format,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.documents.sumOf { it.sizeBytes },
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.IMAGES_TO_PDF,
                payload = ConversionPayload(sourceUris = current.documents.map { it.uri.toString() }),
                outputName = current.suggestedOutputName,
            ),
        )
    }

    fun startPdfToImages(outputTreeUri: Uri) {
        val current = _pdfToImagesDraft.value ?: return
        val ranges = parsePdfPageRanges(current.pageRanges, current.pageCount).getOrElse {
            _message.value = it.message ?: "页码格式不正确"
            return
        }
        val pages = flattenPdfPages(ranges)
        if (pages.size > MAX_EXPORTED_PDF_PAGES) {
            _message.value = "一次最多导出 $MAX_EXPORTED_PDF_PAGES 页"
            return
        }
        persistTreePermission(outputTreeUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = current.targetFormat,
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_TO_IMAGES,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    pageRanges = current.pageRanges,
                    pages = pages,
                    outputTreeUri = outputTreeUri.toString(),
                ),
                outputName = "${pages.size} 个 ${current.targetFormat.displayName} 文件",
            ),
        )
    }

    fun startPdfMerge(outputUri: Uri) {
        val current = _pdfMergeDraft.value ?: return
        if (current.documents.size < 2) return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.documents.first().uri.toString(),
                sourceName = "${current.documents.size} 个 PDF",
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.documents.sumOf { it.sizeBytes },
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_MERGE,
                payload = ConversionPayload(sourceUris = current.documents.map { it.uri.toString() }),
                outputName = current.suggestedOutputName,
            ),
        )
    }

    fun startPdfSplit(outputTreeUri: Uri) {
        val current = _pdfSplitDraft.value ?: return
        val ranges = parsePdfPageRanges(current.pageRanges, current.pageCount).getOrElse {
            _message.value = it.message ?: "页码格式不正确"
            return
        }
        if (ranges.size > MAX_SPLIT_OUTPUTS) {
            _message.value = "一次最多生成 $MAX_SPLIT_OUTPUTS 个 PDF"
            return
        }
        persistTreePermission(outputTreeUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_SPLIT,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    pageRanges = current.pageRanges,
                    outputTreeUri = outputTreeUri.toString(),
                ),
                outputName = "${ranges.size} 个 PDF 文件",
            ),
        )
    }

    fun onPdfToDeletePicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        if (pageCount <= 1) {
            _message.value = "PDF 只有一页，无需删除"
            return false
        }
        _pdfDeleteDraft.value = PdfDeletePagesDraft(document, pageCount)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun togglePdfDeletePage(page: Int) {
        val current = _pdfDeleteDraft.value ?: return
        val selected = if (page in current.selectedPages) {
            current.selectedPages - page
        } else {
            current.selectedPages + page
        }
        _pdfDeleteDraft.value = current.copy(selectedPages = selected)
    }

    fun startPdfDelete(outputUri: Uri) {
        val current = _pdfDeleteDraft.value ?: return
        val pages = current.selectedPages.sorted()
        if (pages.isEmpty()) {
            _message.value = "请先选择要删除的页面"
            return
        }
        if (pages.size >= current.pageCount) {
            _message.value = "不能删除全部页面"
            return
        }
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_DELETE_PAGES,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    pages = pages,
                ),
                outputName = "${current.remaining} 页的 PDF",
            ),
        )
    }

    fun onPdfToRotatePicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        _pdfRotateDraft.value = PdfRotatePagesDraft(document, pageCount)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun selectPdfRotateDegrees(degrees: Int) {
        _pdfRotateDraft.value = _pdfRotateDraft.value?.copy(degrees = degrees)
    }

    fun updatePdfRotateRanges(value: String) {
        _pdfRotateDraft.value = _pdfRotateDraft.value?.copy(pageRanges = value)
    }

    fun startPdfRotate(outputUri: Uri) {
        val current = _pdfRotateDraft.value ?: return
        val pages = if (current.pageRanges.isBlank()) {
            emptyList()
        } else {
            val ranges = parsePdfPageRanges(current.pageRanges, current.pageCount).getOrElse {
                _message.value = it.message ?: "页码格式不正确"
                return
            }
            flattenPdfPages(ranges)
        }
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_ROTATE_PAGES,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    pages = pages,
                    rotateDegrees = current.degrees,
                ),
                outputName = "旋转 ${current.degrees}° 的 PDF",
            ),
        )
    }

    // PDF 2.0: 压缩
    fun onPdfCompressPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        _pdfCompressDraft.value = PdfCompressDraft(document)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun selectPdfCompressPreset(preset: com.openconvert.app.domain.converter.PdfCompressPreset) {
        _pdfCompressDraft.value = _pdfCompressDraft.value?.copy(preset = preset)
    }

    fun startPdfCompress(outputUri: Uri) {
        val current = _pdfCompressDraft.value ?: return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_COMPRESS,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    compressDpi = current.preset.maxDpi,
                    compressQuality = current.preset.quality,
                ),
                outputName = "压缩后的 PDF",
            ),
        )
    }

    // PDF 2.0: 安全 (加密/解密)
    fun onPdfSecurityPicked(uri: Uri, isEncrypt: Boolean): Boolean {
        val document = readPdfDocument(uri) ?: return false
        _pdfSecurityDraft.value = PdfSecurityDraft(document, isEncrypt = isEncrypt)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun setPdfSecurityPassword(password: String) {
        _pdfSecurityDraft.value = _pdfSecurityDraft.value?.copy(password = password)
    }

    fun startPdfSecurity(outputUri: Uri) {
        val current = _pdfSecurityDraft.value ?: return
        if (current.password.isBlank()) {
            _message.value = "请输入密码"
            return
        }
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_SECURITY,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    password = current.password,
                    isEncrypt = current.isEncrypt,
                ),
                outputName = if (current.isEncrypt) "加密保护的 PDF" else "解密后的 PDF",
            ),
        )
    }

    // PDF 2.0: 页面裁剪
    fun onPdfCropPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        _pdfCropDraft.value = PdfCropDraft(document, pageCount)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun setPdfCropMargins(left: Float, top: Float, right: Float, bottom: Float) {
        _pdfCropDraft.value = _pdfCropDraft.value?.copy(
            leftPt = left,
            topPt = top,
            rightPt = right,
            bottomPt = bottom,
        )
    }

    fun startPdfCrop(outputUri: Uri) {
        val current = _pdfCropDraft.value ?: return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_CROP,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    cropMarginsLeft = current.leftPt,
                    cropMarginsTop = current.topPt,
                    cropMarginsRight = current.rightPt,
                    cropMarginsBottom = current.bottomPt,
                ),
                outputName = "裁剪边距后的 PDF",
            ),
        )
    }

    // PDF 2.0: 元数据
    fun onPdfMetadataPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        viewModelScope.launch {
            val meta = runCatching {
                com.openconvert.app.domain.pdf.PdfMetadataManager(app).readMetadata(uri)
            }.getOrElse { com.openconvert.app.domain.pdf.PdfMetadataInfo() }
            _pdfMetadataDraft.value = PdfMetadataDraft(document, meta)
            _conversionState.value = ConversionUiState.Configuring
        }
        return true
    }

    fun startPdfMetadata(outputUri: Uri, title: String, author: String, subject: String, keywords: String) {
        val current = _pdfMetadataDraft.value ?: return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_METADATA,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    metadataTitle = title,
                    metadataAuthor = author,
                    metadataSubject = subject,
                    metadataKeywords = keywords,
                ),
                outputName = "更新元数据后的 PDF",
            ),
        )
    }

    // PDF 2.0: 页面管理器
    fun onPdfPageManagerPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        viewModelScope.launch {
            val pages = runCatching {
                com.openconvert.app.domain.pdf.PdfPageManager(app).parsePages(uri)
            }.getOrElse { emptyList() }
            if (pages.isEmpty()) {
                _message.value = "无法解析 PDF 页面"
                return@launch
            }
            _pdfPageManagerDraft.value = PdfPageManagerDraft(document, pages)
            _conversionState.value = ConversionUiState.Configuring
        }
        return true
    }

    fun reorderPdfPages(fromIndex: Int, toIndex: Int) {
        val current = _pdfPageManagerDraft.value ?: return
        val reordered = com.openconvert.app.domain.pdf.PdfPageManager(app).reorder(current.pages, fromIndex, toIndex)
        _pdfPageManagerDraft.value = current.copy(pages = reordered)
    }

    fun rotatePdfPages(targetIds: Set<String>, delta: Int) {
        val current = _pdfPageManagerDraft.value ?: return
        val rotated = com.openconvert.app.domain.pdf.PdfPageManager(app).rotate(current.pages, targetIds, delta)
        _pdfPageManagerDraft.value = current.copy(pages = rotated)
    }

    fun deletePdfPages(targetIds: Set<String>) {
        val current = _pdfPageManagerDraft.value ?: return
        val deleted = runCatching {
            com.openconvert.app.domain.pdf.PdfPageManager(app).delete(current.pages, targetIds)
        }.getOrElse {
            _message.value = it.message ?: "删除失败"
            return
        }
        _pdfPageManagerDraft.value = current.copy(pages = deleted)
    }

    fun startPdfPageManagerExport(outputUri: Uri) {
        val current = _pdfPageManagerDraft.value ?: return
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.PDF,
                targetFormat = FileFormat.PDF,
                outputUri = outputUri.toString(),
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.PDF_PAGE_MANAGER,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    pages = current.pages.map { it.originalPageIndex },
                    rotateDegrees = current.pages.firstOrNull()?.rotationDegrees ?: 0,
                ),
                outputName = "重新排版的 PDF",
            ),
        )
    }

    // 缓存管理
    fun refreshCacheStats() {
        viewModelScope.launch {
            _cacheStats.value = app.cacheManager.getCacheStats()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            app.cacheManager.clearCache()
            _cacheStats.value = app.cacheManager.getCacheStats()
            _message.value = "缓存已清理"
        }
    }

    fun onFilesToCompressPicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return false
        if (uniqueUris.size > MAX_BATCH_FILES) {
            _message.value = "一次最多选择 $MAX_BATCH_FILES 个文件"
            return false
        }
        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format != FileFormat.UNKNOWN }
        if (documents.isEmpty()) {
            _message.value = "没有读到可用的文件"
            return false
        }
        if (documents.size != uniqueUris.size) {
            _message.value = "已忽略无法读取或不支持的文件"
        }
        val firstTarget = if (documents.size == 1) FileFormat.ZIP else FileFormat.ZIP
        _archiveCompressDraft.value = ArchiveCompressDraft(documents, firstTarget)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun selectArchiveTarget(format: FileFormat) {
        val current = _archiveCompressDraft.value ?: return
        if (format !in setOf(FileFormat.ZIP, FileFormat.TAR, FileFormat.GZIP, FileFormat.BZIP2)) return
        if (format in setOf(FileFormat.GZIP, FileFormat.BZIP2) && current.documents.size > 1) {
            _message.value = "${format.displayName} 只支持单个文件压缩"
            return
        }
        _archiveCompressDraft.value = current.copy(targetFormat = format)
    }

    fun startArchiveCompress(outputUri: Uri) {
        val current = _archiveCompressDraft.value ?: return
        if (current.singleFileOnly && current.documents.size > 1) {
            _message.value = "${current.targetFormat.displayName} 只支持单个文件压缩"
            return
        }
        persistDocumentPermission(outputUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.documents.first().uri.toString(),
                sourceName = if (current.documents.size == 1) {
                    current.documents.first().name
                } else {
                    "${current.documents.size} 个文件"
                },
                sourceFormat = current.documents.first().format,
                targetFormat = current.targetFormat,
                outputUri = outputUri.toString(),
                fileSize = current.documents.sumOf { it.sizeBytes },
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.ARCHIVE_COMPRESS,
                payload = ConversionPayload(
                    sourceUris = current.documents.map { it.uri.toString() },
                    sourceNames = current.documents.map { it.name },
                ),
                outputName = current.suggestedOutputName,
            ),
        )
    }

    fun onArchiveToExtractPicked(uri: Uri): Boolean {
        val document = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            resolver.readSelectedDocument(uri)
        }.getOrElse {
            _message.value = "无法读取所选文件"
            return false
        }
        val lower = document.name.lowercase()
        if (!(lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ||
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2"))
        ) {
            _message.value = "请选择 ZIP / TAR.GZ / TAR.BZ2 压缩包"
            return false
        }
        _archiveExtractDraft.value = ArchiveExtractDraft(document)
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun startArchiveExtract(outputTreeUri: Uri) {
        val current = _archiveExtractDraft.value ?: return
        persistTreePermission(outputTreeUri)
        submit(
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = current.document.uri.toString(),
                sourceName = current.document.name,
                sourceFormat = FileFormat.fromFileName(current.document.name),
                targetFormat = FileFormat.UNKNOWN,
                fileSize = current.document.sizeBytes,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.ARCHIVE_EXTRACT,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    outputTreeUri = outputTreeUri.toString(),
                ),
                outputName = "解压到 ${current.suggestedFolderName}",
            ),
        )
    }

    fun onBatchFilesPicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.size < 2) {
            _message.value = "批量转换至少选择 2 个文件"
            return false
        }
        if (uniqueUris.size > MAX_BATCH_FILES) {
            _message.value = "一次最多选择 $MAX_BATCH_FILES 个文件"
            return false
        }
        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format != FileFormat.UNKNOWN }

        if (documents.isEmpty()) {
            _message.value = "没有读到可用的文件"
            return false
        }
        if (documents.size != uniqueUris.size) {
            _message.value = "已忽略无法读取或不支持的文件"
        }
        val categories = documents.map { it.format.category }.distinct()
        if (categories.size != 1) {
            _message.value = "批量转换需要同一类文件（如图片或视频）"
            return false
        }
        val draft = BatchDraft(
            documents = documents,
            targetFormat = documents.first().format.availableTargets().firstOrNull()
                ?: return false.also { _message.value = "暂不支持 ${documents.first().format.displayName} 批量转换" },
        )
        if (draft.commonFormats.isEmpty()) {
            _message.value = "所选文件没有共同的输出格式"
            return false
        }
        _batchDraft.value = draft
        _batchUiState.value = BatchUiState.Configuring(draft)
        return true
    }

    fun selectBatchTarget(format: FileFormat) {
        val current = _batchDraft.value ?: return
        if (format !in current.commonFormats) return
        _batchDraft.value = current.copy(targetFormat = format)
        _batchUiState.value = BatchUiState.Configuring(current.copy(targetFormat = format))
    }

    fun selectBatchQuality(quality: QualityPreset) {
        val current = _batchDraft.value ?: return
        _batchDraft.value = current.copy(quality = quality)
        _batchUiState.value = BatchUiState.Configuring(current.copy(quality = quality))
    }

    fun selectBatchResolution(resolution: ResolutionPreset) {
        val current = _batchDraft.value ?: return
        _batchDraft.value = current.copy(resolution = resolution)
        _batchUiState.value = BatchUiState.Configuring(current.copy(resolution = resolution))
    }

    fun startBatch(outputTreeUri: Uri) {
        val draft = _batchDraft.value ?: return
        if (!draft.engineAvailable) {
            _message.value = "${draft.targetFormat.displayName} 引擎尚未接入"
            return
        }
        persistTreePermission(outputTreeUri)
        val batchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val settings = BatchSettings(
            sourceUris = draft.documents.map { it.uri.toString() },
            sourceNames = draft.documents.map { it.name },
            sourceFormats = draft.documents.map { it.format.name },
            targetFormat = draft.targetFormat.name,
            quality = draft.quality.name,
            resolution = draft.resolution.name,
            outputTreeUri = outputTreeUri.toString(),
        )
        val job = BatchJob(
            id = batchId,
            name = "${draft.documents.size} 个文件 → ${draft.targetFormat.displayName}",
            status = BatchJobStatus.RUNNING,
            total = draft.documents.size,
            done = 0,
            failed = 0,
            createdAt = now,
            settingsJson = BatchSettingsCodec.encode(settings),
        )
        val tasks = draft.documents.map { document ->
            ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = document.uri.toString(),
                sourceName = document.name,
                sourceFormat = document.format,
                targetFormat = draft.targetFormat,
                fileSize = document.sizeBytes,
                quality = draft.quality,
                resolution = draft.resolution,
                progress = 1,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.SINGLE,
                payload = com.openconvert.app.domain.model.ConversionPayload(
                    batchId = batchId,
                    outputTreeUri = outputTreeUri.toString(),
                ),
                outputName = suggestedOutputName(document.name, draft.targetFormat),
            )
        }
        _batchJobId.value = batchId
        _batchUiState.value = BatchUiState.Running(job, tasks)
        viewModelScope.launch {
            app.historyRepository.saveBatch(job)
            tasks.forEach { app.historyRepository.save(it) }
            app.batchScheduler.enqueueTasks(batchId, tasks)
        }
    }

    fun pauseBatch() {
        val batchId = _batchJobId.value ?: return
        app.batchScheduler.pause(batchId)
    }

    fun resumeBatch() {
        val batchId = _batchJobId.value ?: return
        app.batchScheduler.resume(batchId)
    }

    fun cancelBatch() {
        val batchId = _batchJobId.value ?: return
        app.batchScheduler.cancel(batchId)
    }

    fun observeBatch() {
        val batchId = _batchJobId.value ?: return
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                app.historyRepository.observeBatch(batchId),
                app.historyRepository.observeBatchTasks(batchId),
            ) { job, tasks -> job to tasks }.collect { (job, tasks) ->
                if (job == null) return@collect
                _batchUiState.value = when {
                    job.status == BatchJobStatus.COMPLETED ||
                        job.status == BatchJobStatus.CANCELLED -> BatchUiState.Completed(job, tasks)
                    else -> BatchUiState.Running(job, tasks)
                }
            }
        }
    }

    fun resetBatch() {
        observeJob?.cancel()
        _batchJobId.value = null
        _batchDraft.value = null
        _batchUiState.value = BatchUiState.Idle
    }

    fun cancelConversion() {
        val taskId = trackedTaskId ?: return
        cancelTask(taskId)
    }

    /**
     * 从任务中心取消任意任务（不限于当前追踪的那个）。
     * Room 收尾由 ConversionScheduler.cancel 统一处理（幂等），
     * 这里不重复写状态——历史上取消逻辑散在三处导致过僵尸任务。
     */
    fun cancelTask(taskId: String) {
        app.conversionScheduler.cancel(taskId)
    }

    private fun legacyCancelConversion() {
        val taskId = trackedTaskId ?: return
        app.conversionScheduler.cancel(taskId)
        viewModelScope.launch {
            val task = app.historyRepository.get(taskId) ?: return@launch
            if (task.status == ConversionStatus.RUNNING || task.status == ConversionStatus.PENDING) {
                app.historyRepository.save(
                    task.copy(
                        status = ConversionStatus.CANCELLED,
                        completedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun retryConversion() {
        _conversionState.value = ConversionUiState.Configuring
    }

    fun resetConversion() {
        val current = _conversionState.value
        if (current is ConversionUiState.Running) cancelConversion()
        _conversionState.value = ConversionUiState.Configuring
    }

    fun clearHistory() {
        viewModelScope.launch { app.historyRepository.clearFinished() }
    }

    fun deleteHistory(ids: Collection<String>) {
        viewModelScope.launch { app.historyRepository.deleteFinished(ids) }
    }

    fun reuseConversion(task: ConversionTask): Boolean {
        val uri = runCatching { Uri.parse(task.sourceUri) }.getOrNull()
        if (uri == null) {
            _message.value = "找不到原来的文件"
            return false
        }
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val document = runCatching { resolver.readSelectedDocument(uri) }.getOrElse {
            _message.value = "原文件已不可用，请重新选择"
            return false
        }
        if (document.format == FileFormat.UNKNOWN) {
            _message.value = "暂不支持此文件格式"
            return false
        }
        val target = document.format.availableTargets().let { targets ->
            if (task.targetFormat in targets) task.targetFormat else targets.firstOrNull()
        }
        if (target == null) {
            _message.value = "暂不支持 ${document.format.displayName} 转换"
            return false
        }
        _draft.value = ConversionDraft(
            document = document,
            targetFormat = target,
            quality = task.quality,
            resolution = task.resolution,
        )
        _conversionState.value = ConversionUiState.Configuring
        return true
    }

    fun setImageQualityPreference(quality: QualityPreset) {
        app.userPreferences.setImageQuality(quality)
        val draft = _draft.value
        if (draft != null && draft.document.format.category == FileCategory.IMAGE) {
            _draft.value = draft.copy(quality = quality)
        }
    }

    fun setVideoQualityPreference(quality: QualityPreset) {
        app.userPreferences.setVideoQuality(quality)
        val draft = _draft.value
        if (draft != null && draft.document.format.category != FileCategory.IMAGE) {
            _draft.value = draft.copy(quality = quality)
        }
    }

    private fun defaultQualityFor(format: FileFormat): QualityPreset =
        if (format.category == FileCategory.IMAGE) {
            app.userPreferences.imageQuality.value
        } else {
            app.userPreferences.videoQuality.value
        }

    fun consumeMessage() {
        _message.value = null
    }

    private fun submit(task: ConversionTask) {
        if (!ensureIdle()) return
        trackTask(task.copy(status = ConversionStatus.RUNNING, progress = 1))
        viewModelScope.launch {
            app.historyRepository.save(task)
            app.conversionScheduler.enqueue(task.id)
        }
    }

    private fun trackTask(task: ConversionTask) {
        trackedTaskId = task.id
        applyTask(task)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            app.historyRepository.observe(task.id).collect { latest ->
                if (latest != null) applyTask(latest)
            }
        }
    }

    private fun applyTask(task: ConversionTask) {
        _conversionState.value = when (task.status) {
            ConversionStatus.PENDING, ConversionStatus.RUNNING -> ConversionUiState.Running(task)
            ConversionStatus.COMPLETED -> ConversionUiState.Completed(
                task = task,
                outputName = task.outputName ?: task.sourceName,
                outputUris = task.payload.outputUris.ifEmpty { listOfNotNull(task.outputUri) },
            )
            ConversionStatus.FAILED -> ConversionUiState.Failed(
                task,
                task.errorMessage ?: "转换失败",
            )
            ConversionStatus.CANCELLED -> {
                if (_conversionState.value is ConversionUiState.Running) {
                    _message.value = "转换已取消"
                }
                ConversionUiState.Configuring
            }
        }
    }

    private fun ensureIdle(): Boolean {
        if (_conversionState.value is ConversionUiState.Running) {
            _message.value = "请等待当前转换完成"
            return false
        }
        return true
    }

    private fun readPdfDocument(uri: Uri): SelectedDocument? {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val document = runCatching { resolver.readSelectedDocument(uri) }.getOrElse {
            _message.value = "无法读取所选 PDF"
            return null
        }
        if (document.format != FileFormat.PDF) {
            _message.value = "请选择 PDF 文件"
            return null
        }
        return document
    }

    private fun readPdfPageCount(uri: Uri): Int? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        } ?: error("无法打开 PDF")
    }.getOrElse {
        _message.value = "无法读取 PDF 页数，文件可能已损坏或带密码"
        null
    }

    private fun persistDocumentPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun persistTreePermission(uri: Uri) = persistDocumentPermission(uri)

    private companion object {
        const val MAX_PDF_IMAGES = 50
        const val MAX_MERGE_PDFS = 20
        const val MAX_EXPORTED_PDF_PAGES = 200
        const val MAX_SPLIT_OUTPUTS = 100
        const val MAX_BATCH_FILES = 200
    }
}
