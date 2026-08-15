package com.openconvert.app.domain.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheStats(
    val totalSizeBytes: Long,
    val fileCount: Int,
)

/**
 * 转换缓存与临时文件管理器（计划书 §四十二）。
 */
class CacheManager(
    private val context: Context,
) {
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        var totalSize = 0L
        var count = 0

        cacheDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
                count++
            }
        }
        CacheStats(totalSizeBytes = totalSize, fileCount = count)
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        var success = true
        cacheDir.listFiles()?.forEach { file ->
            // 保留必要的空目录结构，删除所有临时生成文件
            if (file.name.startsWith("pdf_") ||
                file.name.startsWith("media-") ||
                file.name.startsWith("office-") ||
                file.name.startsWith("tmp_")
            ) {
                if (!file.deleteRecursively()) {
                    success = false
                }
            }
        }
        success
    }
}
