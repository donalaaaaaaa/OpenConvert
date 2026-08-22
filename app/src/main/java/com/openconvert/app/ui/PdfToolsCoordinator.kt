package com.openconvert.app.ui

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.data.saf.SelectedDocument
import com.openconvert.app.data.saf.readSelectedDocument
import com.openconvert.app.domain.converter.flattenPdfPages
import com.openconvert.app.domain.converter.parsePdfPageRanges
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.suggestedOutputName
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** PDF 工具会话：合图/拆页/合并/旋转/压缩/加密/裁剪/元数据/页管理。 */
class PdfToolsCoordinator(
    private val host: ConversionHost,
    private val app: OpenConvertApplication,
    private val resolver: android.content.ContentResolver,
    private val scope: CoroutineScope,
) {
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

    private val _pdfWatermarkDraft = MutableStateFlow<PdfWatermarkDraft?>(null)
    val pdfWatermarkDraft: StateFlow<PdfWatermarkDraft?> = _pdfWatermarkDraft.asStateFlow()

    private val _pdfPageManagerDraft = MutableStateFlow<PdfPageManagerDraft?>(null)
    val pdfPageManagerDraft: StateFlow<PdfPageManagerDraft?> = _pdfPageManagerDraft.asStateFlow()

    fun onImagesPicked(uris: List<Uri>): Boolean {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return false
        if (uniqueUris.size > MAX_PDF_IMAGES) {
            host.postedMessage = "一次最多选择 $MAX_PDF_IMAGES 张图片"
            return false
        }

        val documents = uniqueUris.mapNotNull { uri ->
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { resolver.readSelectedDocument(uri) }.getOrNull()
        }.filter { it.format.category == FileCategory.IMAGE }

        if (documents.isEmpty()) {
            host.postedMessage = "没有读到可用的 JPG、PNG 或 WEBP 图片"
            return false
        }
        if (documents.size != uniqueUris.size) {
            host.postedMessage = "已忽略无法读取或不支持的文件"
        }

        _imagesToPdfDraft.value = ImagesToPdfDraft(documents)
        host.uiState = ConversionUiState.Configuring
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
        host.uiState = ConversionUiState.Configuring
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
            host.postedMessage = "请至少选择 2 个 PDF"
            return false
        }
        if (uniqueUris.size > MAX_MERGE_PDFS) {
            host.postedMessage = "一次最多合并 $MAX_MERGE_PDFS 个 PDF"
            return false
        }
        val documents = uniqueUris.mapNotNull(::readPdfDocument)
        if (documents.size != uniqueUris.size) {
            host.postedMessage = "存在无法读取或不是 PDF 的文件"
            return false
        }
        _pdfMergeDraft.value = PdfMergeDraft(documents)
        host.uiState = ConversionUiState.Configuring
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
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun updatePdfSplitRanges(value: String) {
        _pdfSplitDraft.value = _pdfSplitDraft.value?.copy(pageRanges = value)
    }

    fun startImagesToPdf(outputUri: Uri) {
        val current = _imagesToPdfDraft.value ?: return
        if (current.documents.isEmpty()) return
        host.persistDocumentPermission(outputUri)
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
            host.postedMessage = it.message ?: "页码格式不正确"
            return
        }
        val pages = flattenPdfPages(ranges)
        if (pages.size > MAX_EXPORTED_PDF_PAGES) {
            host.postedMessage = "一次最多导出 $MAX_EXPORTED_PDF_PAGES 页"
            return
        }
        host.persistTreePermission(outputTreeUri)
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
        host.persistDocumentPermission(outputUri)
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
            host.postedMessage = it.message ?: "页码格式不正确"
            return
        }
        if (ranges.size > MAX_SPLIT_OUTPUTS) {
            host.postedMessage = "一次最多生成 $MAX_SPLIT_OUTPUTS 个 PDF"
            return
        }
        host.persistTreePermission(outputTreeUri)
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
            host.postedMessage = "PDF 只有一页，无需删除"
            return false
        }
        _pdfDeleteDraft.value = PdfDeletePagesDraft(document, pageCount)
        host.uiState = ConversionUiState.Configuring
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
            host.postedMessage = "请先选择要删除的页面"
            return
        }
        if (pages.size >= current.pageCount) {
            host.postedMessage = "不能删除全部页面"
            return
        }
        host.persistDocumentPermission(outputUri)
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
        host.uiState = ConversionUiState.Configuring
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
                host.postedMessage = it.message ?: "页码格式不正确"
                return
            }
            flattenPdfPages(ranges)
        }
        host.persistDocumentPermission(outputUri)
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
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun selectPdfCompressPreset(preset: com.openconvert.app.domain.converter.PdfCompressPreset) {
        _pdfCompressDraft.value = _pdfCompressDraft.value?.copy(preset = preset)
    }

    fun startPdfCompress(outputUri: Uri) {
        val current = _pdfCompressDraft.value ?: return
        host.persistDocumentPermission(outputUri)
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
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun setPdfSecurityPassword(password: String) {
        _pdfSecurityDraft.value = _pdfSecurityDraft.value?.copy(password = password)
    }

    fun startPdfSecurity(outputUri: Uri) {
        val current = _pdfSecurityDraft.value ?: return
        if (current.password.isBlank()) {
            host.postedMessage = "请输入密码"
            return
        }
        host.persistDocumentPermission(outputUri)
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
        host.uiState = ConversionUiState.Configuring
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
        host.persistDocumentPermission(outputUri)
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
        scope.launch {
            val meta = runCatching {
                com.openconvert.app.domain.pdf.PdfMetadataManager(app).readMetadata(uri)
            }.getOrElse { com.openconvert.app.domain.pdf.PdfMetadataInfo() }
            _pdfMetadataDraft.value = PdfMetadataDraft(document, meta)
            host.uiState = ConversionUiState.Configuring
        }
        return true
    }

    fun startPdfMetadata(outputUri: Uri, title: String, author: String, subject: String, keywords: String) {
        val current = _pdfMetadataDraft.value ?: return
        host.persistDocumentPermission(outputUri)
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

    fun onPdfWatermarkPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        val pageCount = readPdfPageCount(uri) ?: return false
        _pdfWatermarkDraft.value = PdfWatermarkDraft(document, pageCount)
        host.uiState = ConversionUiState.Configuring
        return true
    }

    fun setPdfWatermark(text: String, opacity: Float, position: com.openconvert.app.domain.converter.PdfWatermarkPosition) {
        _pdfWatermarkDraft.value = _pdfWatermarkDraft.value?.copy(
            text = text,
            opacity = opacity,
            position = position,
        )
    }

    fun startPdfWatermark(outputUri: Uri) {
        val current = _pdfWatermarkDraft.value ?: return
        val text = current.text.trim()
        if (text.isEmpty()) {
            host.postedMessage = "请输入水印文字"
            return
        }
        host.persistDocumentPermission(outputUri)
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
                kind = ConversionKind.PDF_WATERMARK,
                payload = ConversionPayload(
                    sourceUris = listOf(current.document.uri.toString()),
                    watermarkText = text,
                    watermarkOpacity = current.opacity,
                    watermarkPosition = current.position.name,
                ),
                outputName = "加水印后的 PDF",
            ),
        )
    }

    // PDF 2.0: 页面管理器
    fun onPdfPageManagerPicked(uri: Uri): Boolean {
        val document = readPdfDocument(uri) ?: return false
        scope.launch {
            val pages = runCatching {
                com.openconvert.app.domain.pdf.PdfPageManager(app).parsePages(uri)
            }.getOrElse { emptyList() }
            if (pages.isEmpty()) {
                host.postedMessage = "无法解析 PDF 页面"
                return@launch
            }
            _pdfPageManagerDraft.value = PdfPageManagerDraft(document, pages)
            host.uiState = ConversionUiState.Configuring
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
            host.postedMessage = it.message ?: "删除失败"
            return
        }
        _pdfPageManagerDraft.value = current.copy(pages = deleted)
    }

    fun startPdfPageManagerExport(outputUri: Uri) {
        val current = _pdfPageManagerDraft.value ?: return
        host.persistDocumentPermission(outputUri)
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

    private fun readPdfDocument(uri: Uri): SelectedDocument? {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val document = runCatching { resolver.readSelectedDocument(uri) }.getOrElse {
            host.postedMessage = "无法读取所选 PDF"
            return null
        }
        if (document.format != FileFormat.PDF) {
            host.postedMessage = "请选择 PDF 文件"
            return null
        }
        return document
    }

    private fun readPdfPageCount(uri: Uri): Int? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        } ?: error("无法打开 PDF")
    }.getOrElse {
        host.postedMessage = "无法读取 PDF 页数，文件可能已损坏或带密码"
        null
    }


    private fun submit(task: ConversionTask) = host.submit(task)

    private companion object {
        const val MAX_PDF_IMAGES = 50
        const val MAX_MERGE_PDFS = 20
        const val MAX_EXPORTED_PDF_PAGES = 200
        const val MAX_SPLIT_OUTPUTS = 100
    }
}
