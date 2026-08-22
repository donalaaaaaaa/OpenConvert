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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenConvertApplication
    private val resolver = application.contentResolver
    private val host = ConversionHost(app, viewModelScope)
    private val pdf = PdfToolsCoordinator(host, app, resolver, viewModelScope)
    private val convert = ConvertCoordinator(host, app, resolver, viewModelScope)

    val draft get() = convert.draft
    val archiveCompressDraft get() = convert.archiveCompressDraft
    val archiveExtractDraft get() = convert.archiveExtractDraft
    val batchDraft get() = convert.batchDraft
    val batchJobId get() = convert.batchJobId
    val batchUiState get() = convert.batchUiState
    val pickedFile get() = convert.pickedFile
    val pickedCapabilities get() = convert.pickedCapabilities
    val imageQualityPreference get() = convert.imageQualityPreference
    val videoQualityPreference get() = convert.videoQualityPreference
    val presets get() = convert.presets
    val appliedPresetId get() = convert.appliedPresetId
    val deviceProfile get() = convert.deviceProfile

    fun inspectFile(uri: Uri) = convert.inspectFile(uri)
    fun chooseConvertTarget(format: FileFormat) = convert.chooseConvertTarget(format)
    fun clearPickedFile() = convert.clearPickedFile()
    fun onDocumentPicked(uri: Uri) = convert.onDocumentPicked(uri)
    fun selectTarget(format: FileFormat) = convert.selectTarget(format)
    fun selectQuality(quality: QualityPreset) = convert.selectQuality(quality)
    fun selectResolution(resolution: ResolutionPreset) = convert.selectResolution(resolution)
    fun selectRotate(degrees: Int) = convert.selectRotate(degrees)
    fun selectCropAspect(aspect: String) = convert.selectCropAspect(aspect)
    fun selectFlip(flip: Int) = convert.selectFlip(flip)
    fun selectStripMetadata(strip: Boolean) = convert.selectStripMetadata(strip)
    fun applyPreset(preset: com.openconvert.app.domain.preset.Preset) = convert.applyPreset(preset)
    fun presetsForCurrentDraft() = convert.presetsForCurrentDraft()
    fun saveDraftAsPreset(name: String) = convert.saveDraftAsPreset(name)
    fun deletePreset(preset: com.openconvert.app.domain.preset.Preset) = convert.deletePreset(preset)
    fun setDefaultPreset(preset: com.openconvert.app.domain.preset.Preset) = convert.setDefaultPreset(preset)
    fun startConversion(outputUri: Uri) = convert.startConversion(outputUri)
    fun onFilesToCompressPicked(uris: List<Uri>) = convert.onFilesToCompressPicked(uris)
    fun selectArchiveTarget(format: FileFormat) = convert.selectArchiveTarget(format)
    fun startArchiveCompress(outputUri: Uri) = convert.startArchiveCompress(outputUri)
    fun onArchiveToExtractPicked(uri: Uri) = convert.onArchiveToExtractPicked(uri)
    fun startArchiveExtract(outputTreeUri: Uri) = convert.startArchiveExtract(outputTreeUri)
    fun onBatchFilesPicked(uris: List<Uri>) = convert.onBatchFilesPicked(uris)
    fun selectBatchTarget(format: FileFormat) = convert.selectBatchTarget(format)
    fun selectBatchQuality(quality: QualityPreset) = convert.selectBatchQuality(quality)
    fun selectBatchResolution(resolution: ResolutionPreset) = convert.selectBatchResolution(resolution)
    fun applyBatchPreset(preset: com.openconvert.app.domain.preset.Preset) = convert.applyBatchPreset(preset)
    fun presetsForBatch() = convert.presetsForBatch()
    fun startBatch(outputTreeUri: Uri) = convert.startBatch(outputTreeUri)
    fun pauseBatch() = convert.pauseBatch()
    fun resumeBatch() = convert.resumeBatch()
    fun cancelBatch() = convert.cancelBatch()
    fun observeBatch() = convert.observeBatch()
    fun resetBatch() = convert.resetBatch()
    fun reuseConversion(task: ConversionTask) = convert.reuseConversion(task)
    fun setImageQualityPreference(quality: QualityPreset) = convert.setImageQualityPreference(quality)
    fun setVideoQualityPreference(quality: QualityPreset) = convert.setVideoQualityPreference(quality)


    val imagesToPdfDraft get() = pdf.imagesToPdfDraft
    val pdfToImagesDraft get() = pdf.pdfToImagesDraft
    val pdfMergeDraft get() = pdf.pdfMergeDraft
    val pdfSplitDraft get() = pdf.pdfSplitDraft
    val pdfDeleteDraft get() = pdf.pdfDeleteDraft
    val pdfRotateDraft get() = pdf.pdfRotateDraft
    val pdfCompressDraft get() = pdf.pdfCompressDraft
    val pdfSecurityDraft get() = pdf.pdfSecurityDraft
    val pdfCropDraft get() = pdf.pdfCropDraft
    val pdfMetadataDraft get() = pdf.pdfMetadataDraft
    val pdfPageManagerDraft get() = pdf.pdfPageManagerDraft

    fun onImagesPicked(uris: List<Uri>) = pdf.onImagesPicked(uris)
    fun movePdfImage(index: Int, direction: Int) = pdf.movePdfImage(index, direction)
    fun removePdfImage(index: Int) = pdf.removePdfImage(index)
    fun onPdfToImagesPicked(uri: Uri) = pdf.onPdfToImagesPicked(uri)
    fun selectPdfImageFormat(format: FileFormat) = pdf.selectPdfImageFormat(format)
    fun updatePdfImageRanges(value: String) = pdf.updatePdfImageRanges(value)
    fun onPdfsToMergePicked(uris: List<Uri>) = pdf.onPdfsToMergePicked(uris)
    fun moveMergePdf(index: Int, direction: Int) = pdf.moveMergePdf(index, direction)
    fun removeMergePdf(index: Int) = pdf.removeMergePdf(index)
    fun onPdfToSplitPicked(uri: Uri) = pdf.onPdfToSplitPicked(uri)
    fun updatePdfSplitRanges(value: String) = pdf.updatePdfSplitRanges(value)
    fun startImagesToPdf(outputUri: Uri) = pdf.startImagesToPdf(outputUri)
    fun startPdfToImages(outputTreeUri: Uri) = pdf.startPdfToImages(outputTreeUri)
    fun startPdfMerge(outputUri: Uri) = pdf.startPdfMerge(outputUri)
    fun startPdfSplit(outputTreeUri: Uri) = pdf.startPdfSplit(outputTreeUri)
    fun onPdfToDeletePicked(uri: Uri) = pdf.onPdfToDeletePicked(uri)
    fun togglePdfDeletePage(page: Int) = pdf.togglePdfDeletePage(page)
    fun startPdfDelete(outputUri: Uri) = pdf.startPdfDelete(outputUri)
    fun onPdfToRotatePicked(uri: Uri) = pdf.onPdfToRotatePicked(uri)
    fun selectPdfRotateDegrees(degrees: Int) = pdf.selectPdfRotateDegrees(degrees)
    fun updatePdfRotateRanges(value: String) = pdf.updatePdfRotateRanges(value)
    fun startPdfRotate(outputUri: Uri) = pdf.startPdfRotate(outputUri)
    fun onPdfCompressPicked(uri: Uri) = pdf.onPdfCompressPicked(uri)
    fun selectPdfCompressPreset(preset: com.openconvert.app.domain.converter.PdfCompressPreset) = pdf.selectPdfCompressPreset(preset)
    fun startPdfCompress(outputUri: Uri) = pdf.startPdfCompress(outputUri)
    fun onPdfSecurityPicked(uri: Uri, isEncrypt: Boolean) = pdf.onPdfSecurityPicked(uri, isEncrypt)
    fun setPdfSecurityPassword(password: String) = pdf.setPdfSecurityPassword(password)
    fun startPdfSecurity(outputUri: Uri) = pdf.startPdfSecurity(outputUri)
    fun onPdfCropPicked(uri: Uri) = pdf.onPdfCropPicked(uri)
    fun setPdfCropMargins(left: Float, top: Float, right: Float, bottom: Float) = pdf.setPdfCropMargins(left, top, right, bottom)
    fun startPdfCrop(outputUri: Uri) = pdf.startPdfCrop(outputUri)
    fun onPdfMetadataPicked(uri: Uri) = pdf.onPdfMetadataPicked(uri)
    fun startPdfMetadata(outputUri: Uri, title: String, author: String, subject: String, keywords: String) = pdf.startPdfMetadata(outputUri, title, author, subject, keywords)
    fun onPdfPageManagerPicked(uri: Uri) = pdf.onPdfPageManagerPicked(uri)
    fun reorderPdfPages(fromIndex: Int, toIndex: Int) = pdf.reorderPdfPages(fromIndex, toIndex)
    fun rotatePdfPages(targetIds: Set<String>, delta: Int) = pdf.rotatePdfPages(targetIds, delta)
    fun deletePdfPages(targetIds: Set<String>) = pdf.deletePdfPages(targetIds)
    fun startPdfPageManagerExport(outputUri: Uri) = pdf.startPdfPageManagerExport(outputUri)




    val conversionState: StateFlow<ConversionUiState> = host.conversionState
    val message: StateFlow<String?> = host.message

    /**
     * 首页 UI 2.0（计划书 §六）：用户选中的文件及其可执行能力。
     * 文件驱动 —— 先选文件，再由 FileCapabilityResolver 告诉用户能做什么。
     */

    private val _cacheStats = MutableStateFlow<com.openconvert.app.domain.cache.CacheStats?>(null)
    val cacheStats: StateFlow<com.openconvert.app.domain.cache.CacheStats?> = _cacheStats.asStateFlow()

    private val benchmarkExporter =
        com.openconvert.app.domain.benchmark.BenchmarkReportExporter(application)
    private val _benchmarkRecordCount = MutableStateFlow(0)
    val benchmarkRecordCount: StateFlow<Int> = _benchmarkRecordCount.asStateFlow()

    val history = app.historyRepository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )


    init {
        viewModelScope.launch {
            app.historyRepository.findActive().firstOrNull()?.let(::trackTask)
        }
    }




    fun refreshCacheStats() {
        viewModelScope.launch {
            _cacheStats.value = app.cacheManager.getCacheStats()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            app.cacheManager.clearCache()
            _cacheStats.value = app.cacheManager.getCacheStats()
            host.postedMessage = "缓存已清理"
        }
    }

    /** 设置页进入时刷新；Benchmark 使用轻量 JSONL，不值得为计数常驻文件观察器。 */
    fun refreshBenchmarkStats() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _benchmarkRecordCount.value = benchmarkExporter.recordCount()
        }
    }

    fun exportBenchmarkReport(
        outputUri: Uri,
        format: com.openconvert.app.domain.benchmark.BenchmarkReportFormat,
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            benchmarkExporter.export(outputUri, format).fold(
                onSuccess = { exported ->
                    val label = when (exported.format) {
                        com.openconvert.app.domain.benchmark.BenchmarkReportFormat.MARKDOWN -> "Markdown"
                        com.openconvert.app.domain.benchmark.BenchmarkReportFormat.CSV -> "CSV"
                    }
                    host.postedMessage = "已导出 $label 报告（${exported.recordCount} 条记录）"
                },
                onFailure = { error ->
                    host.postedMessage = error.message ?: "Benchmark 报告导出失败"
                },
            )
        }
    }


    fun cancelConversion() = host.cancelConversion()

    /**
     * 从任务中心取消任意任务（不限于当前追踪的那个）。
     * Room 收尾由 ConversionScheduler.cancel 统一处理（幂等），
     * 这里不重复写状态——历史上取消逻辑散在三处导致过僵尸任务。
     */
    fun cancelTask(taskId: String) = host.cancelTask(taskId)

    fun retryConversion() = host.retryConversion()

    fun resetConversion() = host.resetConversion()

    fun clearHistory() {
        viewModelScope.launch { app.historyRepository.clearFinished() }
    }

    fun deleteHistory(ids: Collection<String>) {
        viewModelScope.launch { app.historyRepository.deleteFinished(ids) }
    }


    fun consumeMessage() = host.consumeMessage()

    private fun trackTask(task: ConversionTask) = host.trackTask(task)
}
