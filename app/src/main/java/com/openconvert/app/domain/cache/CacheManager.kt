package com.openconvert.app.domain.cache

import android.content.Context
import com.openconvert.app.domain.work.TempWorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CacheStats(
    val totalSizeBytes: Long,
    val fileCount: Int,
)

/**
 * 转换缓存与临时文件管理器（计划书 §四十二）。
 */
class CacheManager(
    private val context: Context,
    private val workspaces: TempWorkspaceManager = TempWorkspaceManager(context),
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
        var success = workspaces.cleanupAll()
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("pdf_") ||
                file.name.startsWith("media-") ||
                file.name.startsWith("office-") ||
                file.name.startsWith("tmp_") ||
                file.name == TempWorkspaceManager.ROOT_NAME
            ) {
                if (!file.deleteRecursively()) {
                    success = false
                }
            }
        }
        success
    }
}
