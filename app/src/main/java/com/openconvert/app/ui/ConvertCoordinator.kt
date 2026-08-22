package com.openconvert.app.ui

import android.content.Intent
import android.net.Uri
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.data.saf.SelectedDocument
import com.openconvert.app.data.saf.readSelectedDocument
import com.openconvert.app.domain.capability.FileCapabilityResolver
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
import com.openconvert.app.domain.model.suggestedOutputName
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页文件驱动、单文件转换、预设、压缩包、批量。 */
class ConvertCoordinator(
    private val host: ConversionHost,
    private val app: OpenConvertApplication,
    private val resolver: android.content.ContentResolver,
    private val scope: CoroutineScope,
) {
    private var batchObserveJob: Job? = null

    private val _draft = MutableStateFlow<ConversionDraft?>(null)
    val draft: StateFlow<ConversionDraft?> = _draft.asStateFlow()

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

    private val _pickedFile = MutableStateFlow<SelectedDocument?>(null)
    val pickedFile: StateFlow<SelectedDocument?> = _pickedFile.asStateFlow()

    private val _pickedCapabilities =
        MutableStateFlow<com.openconvert.app.domain.capability.FileCapabilities?>(null)
    val pickedCapabilities: StateFlow<com.openconvert.app.domain.capability.FileCapabilities?> =
        _pickedCapabilities.asStateFlow()

    val deviceProfile = com.openconvert.app.domain.device.DeviceCapabilities.getHardwareProfile()

    val imageQualityPreference = app.userPreferences.imageQuality
    val videoQualityPreference = app.userPreferences.videoQuality

    /** 预设列表（计划书 §八）。内置 + 用户自定义，均来自 Room。 */
    val presets = app.presetStore.presets.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** 当前转换草稿已应用的预设 id；null = 手动设置。 */
    private val _appliedPresetId = MutableStateFlow<String?>(null)
    val appliedPresetId: StateFlow<String?> = _appliedPresetId.asStateFlow()

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
                host.postedMessage = "无法读取所选文件"
                return false
            }

        val capabilities = FileCapabilityResolver.resolve(document.format)
        if (!capabilities.hasAnything) {
            host.postedMessage = if (document.format == FileFormat.UNKNOWN) {
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
        if (!FileCapabilityResolver.canConvertInEdition(document.format, format)) {
            host.postedMessage = if (document.format.category == FileCategory.OFFICE) {
                "当前为轻量版，Office 转换需要安装 Office 版"
            } else {
                "暂不支持 ${document.format.displayName} → ${format.displayName}"
            }
            return false
        }
        _draft.value = ConversionDraft(document, format, defaultQualityFor(document.format))
        host.uiState = ConversionUiState.Configuring
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
                host.postedMessage = "无法读取所选文件"
                return false
            }

        if (document.format == FileFormat.UNKNOWN) {
            host.postedMessage = "暂不支持此文件格式"
            return false
        }

        val firstTarget = FileCapabilityResolver.targetsForEdition(document.format).firstOrNull()
        if (firstTarget == null) {
            // 有工具能力但没有一进一出的转换边（典型：PDF）——引导到对应工具页，
            // 而不是笼统地说「不支持」。
            host.postedMessage = if (document.format.category == FileCategory.OFFICE) {
                "当前为轻量版，Office 转换需要安装 Office 版"
            } else if (com.openconvert.app.domain.model.ConversionGraph.toolsFor(document.format).size > 1) {
                "${document.format.displayName} 请在工具页处理"
            } else {
                "暂不支持 ${document.format.displayName} 转换"
            }
            return false
        }

        _draft.value = ConversionDraft(document, firstTarget, defaultQualityFor(document.format))
        host.uiState = ConversionUiState.Configuring
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
            host.postedMessage =
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
            host.postedMessage = "请输入预设名称"
            return
        }
        scope.launch {
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
            host.postedMessage = "已保存预设「${saved.name}」"
        }
    }

    fun deletePreset(preset: com.openconvert.app.domain.preset.Preset) {
        scope.launch {
            val deleted = app.presetStore.deleteCustom(preset.id)
            host.postedMessage = if (deleted) {
                "已删除预设「${preset.name}」"
            } else {
                "内置预设不可删除"
            }
        }
    }

    fun setDefaultPreset(preset: com.openconvert.app.domain.preset.Preset) {
        scope.launch {
            app.presetStore.setDefault(preset)
            host.postedMessage = "「${preset.name}」已设为默认"
        }
    }

    fun startConversion(outputUri: Uri) {
        val current = _draft.value ?: return
        if (!current.engineAvailable) {
            host.postedMessage = "${current.document.format.displayName} → ${current.targetFormat.displayName} 引擎尚未接入"
            return
        }
        host.persistDocumentPermission(outputUri)
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

    fun onFilesToCompressPicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return false
        if (uniqueUris.size > MAX_BATCH_FILES) {
            host.postedMessage = "一次最多选择 $MAX_BATCH_FILES 个文件"
            return false
        }
        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format != FileFormat.UNKNOWN }
        if (documents.isEmpty()) {
            host.postedMessage = "没有读到可用的文件"
            return false
        }
        if (documents.size != uniqueUris.size) {
            host.postedMessage = "已忽略无法读取或不支持的文件"
        }
        val firstTarget = if (documents.size == 1) FileFormat.ZIP else FileFormat.ZIP
        _archiveCompressDraft.value = ArchiveCompressDraft(documents, firstTarget)
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun selectArchiveTarget(format: FileFormat) {
        val current = _archiveCompressDraft.value ?: return
        if (format !in setOf(
                FileFormat.ZIP,
                FileFormat.TAR,
                FileFormat.SEVEN_Z,
                FileFormat.GZIP,
                FileFormat.BZIP2,
                FileFormat.XZ,
            )
        ) return
        if (format in setOf(FileFormat.GZIP, FileFormat.BZIP2, FileFormat.XZ) && current.documents.size > 1) {
            host.postedMessage = "${format.displayName} 只支持单个文件压缩"
            return
        }
        _archiveCompressDraft.value = current.copy(targetFormat = format)
    }

    fun startArchiveCompress(outputUri: Uri) {
        val current = _archiveCompressDraft.value ?: return
        if (current.singleFileOnly && current.documents.size > 1) {
            host.postedMessage = "${current.targetFormat.displayName} 只支持单个文件压缩"
            return
        }
        host.persistDocumentPermission(outputUri)
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
            host.postedMessage = "无法读取所选文件"
            return false
        }
        val lower = document.name.lowercase()
        if (!(lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ||
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2"))
        ) {
            host.postedMessage = "请选择 ZIP / TAR.GZ / TAR.BZ2 压缩包"
            return false
        }
        _archiveExtractDraft.value = ArchiveExtractDraft(document)
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun startArchiveExtract(outputTreeUri: Uri) {
        val current = _archiveExtractDraft.value ?: return
        host.persistTreePermission(outputTreeUri)
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
            host.postedMessage = "批量转换至少选择 2 个文件"
            return false
        }
        if (uniqueUris.size > MAX_BATCH_FILES) {
            host.postedMessage = "一次最多选择 $MAX_BATCH_FILES 个文件"
            return false
        }
        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format != FileFormat.UNKNOWN }

        if (documents.isEmpty()) {
            host.postedMessage = "没有读到可用的文件"
            return false
        }
        if (documents.size != uniqueUris.size) {
            host.postedMessage = "已忽略无法读取或不支持的文件"
        }
        val categories = documents.map { it.format.category }.distinct()
        if (categories.size != 1) {
            host.postedMessage = "批量转换需要同一类文件（如图片或视频）"
            return false
        }
        val draft = BatchDraft(
            documents = documents,
            targetFormat = FileCapabilityResolver.targetsForEdition(documents.first().format).firstOrNull()
                ?: return false.also { host.postedMessage = "暂不支持 ${documents.first().format.displayName} 批量转换" },
        )
        if (draft.commonFormats.isEmpty()) {
            host.postedMessage = "所选文件没有共同的输出格式"
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

    /**
     * 批量应用预设（计划书 §8.3）：选 50 张图 + 微信发送预设 → 全部自动处理。
     *
     * 预设的目标格式必须是**所有**输入文件的共同可达格式，否则部分文件会失败；
     * 因此这里用 commonFormats 校验，而不是逐个文件判断。
     */
    fun applyBatchPreset(preset: com.openconvert.app.domain.preset.Preset): Boolean {
        val current = _batchDraft.value ?: return false
        if (preset !in availableBatchPresets(
                sourceFormats = current.documents.map { it.format },
                commonFormats = current.commonFormats,
                presets = listOf(preset),
            )
        ) {
            host.postedMessage =
                "「${preset.name}」不适用于所选全部文件"
            return false
        }
        val updated = current.copy(
            targetFormat = preset.targetFormat,
            quality = preset.quality,
            resolution = preset.resolution,
            presetId = preset.id,
            longestEdgePx = preset.longestEdgePx,
            fixedWidthPx = preset.fixedWidthPx,
            fixedHeightPx = preset.fixedHeightPx,
            cropAspect = preset.cropAspect,
            stripMetadata = preset.stripMetadata,
        )
        _batchDraft.value = updated
        _batchUiState.value = BatchUiState.Configuring(updated)
        return true
    }

    /** 当前批量草稿可用的预设：类别一致且目标格式为全体共同可达。 */
    fun presetsForBatch(): List<com.openconvert.app.domain.preset.Preset> {
        val draft = _batchDraft.value ?: return emptyList()
        return availableBatchPresets(
            sourceFormats = draft.documents.map { it.format },
            commonFormats = draft.commonFormats,
            presets = presets.value,
        )
    }

    fun startBatch(outputTreeUri: Uri) {
        val draft = _batchDraft.value ?: return
        if (!draft.engineAvailable) {
            host.postedMessage = "${draft.targetFormat.displayName} 引擎尚未接入"
            return
        }
        host.persistTreePermission(outputTreeUri)
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
                    // §8.3：预设的尺寸约束随每个子任务下传，否则批量应用只改格式不改尺寸。
                    presetId = draft.presetId,
                    longestEdgePx = draft.longestEdgePx,
                    fixedWidthPx = draft.fixedWidthPx,
                    fixedHeightPx = draft.fixedHeightPx,
                    cropAspect = draft.cropAspect,
                    stripMetadata = draft.stripMetadata,
                ),
                outputName = suggestedOutputName(document.name, draft.targetFormat),
            )
        }
        _batchJobId.value = batchId
        _batchUiState.value = BatchUiState.Running(job, tasks)
        scope.launch {
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
        batchObserveJob?.cancel()
        batchObserveJob = scope.launch {
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
        batchObserveJob?.cancel()
        _batchJobId.value = null
        _batchDraft.value = null
        _batchUiState.value = BatchUiState.Idle
    }

    fun reuseConversion(task: ConversionTask): Boolean {
        val uri = runCatching { Uri.parse(task.sourceUri) }.getOrNull()
        if (uri == null) {
            host.postedMessage = "找不到原来的文件"
            return false
        }
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val document = runCatching { resolver.readSelectedDocument(uri) }.getOrElse {
            host.postedMessage = "原文件已不可用，请重新选择"
            return false
        }
        if (document.format == FileFormat.UNKNOWN) {
            host.postedMessage = "暂不支持此文件格式"
            return false
        }
        val target = FileCapabilityResolver.targetsForEdition(document.format).let { targets ->
            if (task.targetFormat in targets) task.targetFormat else targets.firstOrNull()
        }
        if (target == null) {
            host.postedMessage = if (document.format.category == FileCategory.OFFICE) {
                "当前为轻量版，Office 转换记录需要安装 Office 版才能重试"
            } else {
                "暂不支持 ${document.format.displayName} 转换"
            }
            return false
        }
        _draft.value = ConversionDraft(
            document = document,
            targetFormat = target,
            quality = task.quality,
            resolution = task.resolution,
        )
        host.uiState = ConversionUiState.Configuring
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


    private fun submit(task: ConversionTask) = host.submit(task)

    private companion object {
        const val MAX_BATCH_FILES = 200
    }
}
