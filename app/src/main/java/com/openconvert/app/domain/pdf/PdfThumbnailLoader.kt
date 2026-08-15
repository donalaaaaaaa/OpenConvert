package com.openconvert.app.domain.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 统一 PDF 缩略图异步加载与 LRU 内存缓存器（计划书 §五、§六）。
 * 针对 500+ 页大型 PDF 采用按需懒加载与内存上限控制，彻底规避 OOM。
 */
class PdfThumbnailLoader(
    private val context: Context,
    private val uri: Uri,
    maxMemoryBytes: Int = DEFAULT_MAX_MEMORY_BYTES,
) : Closeable {

    private val mutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var _pageCount: Int = 0

    val pageCount: Int
        get() = _pageCount

    // LRU 缓存：按 Bitmap 字节大小进行限制
    private val cache = object : LruCache<Int, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    suspend fun initialize(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (renderer == null) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("无法读取 PDF 文件描述符")
                pfd = descriptor
                val pdfRenderer = PdfRenderer(descriptor)
                renderer = pdfRenderer
                _pageCount = pdfRenderer.pageCount
            }
            _pageCount
        }
    }

    suspend fun loadThumbnail(
        pageIndex: Int,
        maxDimension: Int = THUMBNAIL_MAX_EDGE,
        rotationDegrees: Int = 0,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = pageIndex * 360 + (rotationDegrees % 360)
        cache.get(cacheKey)?.let { return@withContext it }

        mutex.withLock {
            val curRenderer = renderer ?: run {
                initialize()
                renderer
            } ?: return@withContext null

            if (pageIndex < 0 || pageIndex >= _pageCount) return@withContext null

            val rendered = curRenderer.openPage(pageIndex).use { page ->
                val scale = min(1.0f, maxDimension.toFloat() / maxOf(page.width, page.height))
                val targetW = (page.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) bitmap.recycle()
                    rotated
                } else {
                    bitmap
                }
            }

            cache.put(cacheKey, rendered)
            rendered
        }
    }

    fun getCachedThumbnail(pageIndex: Int, rotationDegrees: Int = 0): Bitmap? {
        val cacheKey = pageIndex * 360 + (rotationDegrees % 360)
        return cache.get(cacheKey)
    }

    fun clearCache() {
        cache.evictAll()
    }

    override fun close() {
        clearCache()
        renderer?.close()
        renderer = null
        pfd?.close()
        pfd = null
    }

    companion object {
        const val DEFAULT_MAX_MEMORY_BYTES = 32 * 1024 * 1024 // 32MB 内存上限
        const val THUMBNAIL_MAX_EDGE = 320 // 缩略图最大边长
    }
}
