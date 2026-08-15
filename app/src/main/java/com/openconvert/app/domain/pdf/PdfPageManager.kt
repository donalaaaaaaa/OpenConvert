package com.openconvert.app.domain.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

data class PdfPageItem(
    val id: String, // 唯一标识
    val originalPageIndex: Int, // 在原始 PDF 中的 0-indexed 页码
    val rotationDegrees: Int = 0, // 增量旋转角度 (0, 90, 180, 270)
    val width: Int = 0,
    val height: Int = 0,
    val isSelected: Boolean = false,
)

/**
 * 统一 PDF 页面管理器（计划书 §五）。
 * 将排序、旋转、删除、选择与重组统一为单一流水线，避免分散逻辑。
 */
class PdfPageManager(
    private val context: Context,
) {
    private val resolver = context.contentResolver

    /**
     * 解析 PDF 文件所有页面的基本元数据。
     */
    suspend fun parsePages(uri: Uri): List<PdfPageItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PdfPageItem>()
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        items += PdfPageItem(
                            id = "page_$i",
                            originalPageIndex = i,
                            rotationDegrees = 0,
                            width = page.width,
                            height = page.height,
                            isSelected = false,
                        )
                    }
                }
            }
        } ?: throw FileNotFoundException("无法读取源 PDF 文件")
        items
    }

    /**
     * 拖拽重新排序
     */
    fun reorder(pages: List<PdfPageItem>, fromIndex: Int, toIndex: Int): List<PdfPageItem> {
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) {
            return pages
        }
        val mutable = pages.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    /**
     * 旋转指定页面
     */
    fun rotate(pages: List<PdfPageItem>, targetIds: Set<String>, deltaDegrees: Int): List<PdfPageItem> {
        return pages.map { page ->
            if (page.id in targetIds) {
                val newRot = (page.rotationDegrees + deltaDegrees).let { (it % 360 + 360) % 360 }
                page.copy(rotationDegrees = newRot)
            } else {
                page
            }
        }
    }

    /**
     * 删除指定页面（至少保留 1 页）
     */
    fun delete(pages: List<PdfPageItem>, targetIds: Set<String>): List<PdfPageItem> {
        val remaining = pages.filterNot { it.id in targetIds }
        if (remaining.isEmpty()) {
            throw IllegalArgumentException("PDF 至少需要保留 1 页")
        }
        return remaining
    }

    /**
     * 切换选择状态
     */
    fun toggleSelection(pages: List<PdfPageItem>, targetId: String): List<PdfPageItem> {
        return pages.map {
            if (it.id == targetId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun selectAll(pages: List<PdfPageItem>, selected: Boolean): List<PdfPageItem> {
        return pages.map { it.copy(isSelected = selected) }
    }

    /**
     * 将页面排布与旋转应用到新 PDF 并导出
     */
    suspend fun exportPdf(
        inputUri: Uri,
        outputUri: Uri,
        pageLayout: List<PdfPageItem>,
        onProgress: suspend (Int) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        if (pageLayout.isEmpty()) throw IllegalArgumentException("没有可导出的页面")

        val tempFile = File(context.cacheDir, "pdf_manager_${System.currentTimeMillis()}.pdf")
        try {
            onProgress(10)
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { sourceDoc ->
                    val pageCount = sourceDoc.numberOfPages
                    PDDocument().use { targetDoc ->
                        pageLayout.forEachIndexed { index, pageItem ->
                            if (pageItem.originalPageIndex in 0 until pageCount) {
                                val page: PDPage = sourceDoc.getPage(pageItem.originalPageIndex)
                                targetDoc.importPage(page)
                                val importedPage = targetDoc.getPage(targetDoc.numberOfPages - 1)
                                if (pageItem.rotationDegrees != 0) {
                                    val currentRot = importedPage.rotation
                                    importedPage.rotation = (currentRot + pageItem.rotationDegrees) % 360
                                }
                            }
                            val progress = 10 + ((index + 1) * 75 / pageLayout.size)
                            onProgress(progress.coerceIn(10, 85))
                        }

                        onProgress(88)
                        tempFile.outputStream().use { out ->
                            targetDoc.save(out)
                        }
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            onProgress(95)
            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            val size = tempFile.length()
            onProgress(100)
            size
        } finally {
            tempFile.delete()
        }
    }
}
