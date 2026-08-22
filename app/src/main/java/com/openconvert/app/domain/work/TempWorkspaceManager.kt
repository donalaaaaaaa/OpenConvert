package com.openconvert.app.domain.work

import android.content.Context
import java.io.File

/**
 * 转换临时目录。所有 scratch 文件进 `cacheDir/oc-workspaces/`，
 * 任务结束或用户清缓存时整棵删掉，避免各 Converter 自己 invent 文件名。
 */
class TempWorkspaceManager(private val root: File) {
    constructor(context: Context) : this(File(context.cacheDir, ROOT_NAME))

    fun directory(namespace: String, taskId: String): File {
        val dir = File(File(root, sanitize(namespace)), sanitize(taskId))
        if (!dir.mkdirs() && !dir.isDirectory) {
            error("无法创建临时目录: ${dir.absolutePath}")
        }
        return dir
    }

    fun file(namespace: String, name: String): File {
        val dir = File(root, sanitize(namespace))
        if (!dir.mkdirs() && !dir.isDirectory) {
            error("无法创建临时目录: ${dir.absolutePath}")
        }
        return File(dir, name)
    }

    fun cleanup(namespace: String, taskId: String): Boolean =
        File(File(root, sanitize(namespace)), sanitize(taskId)).deleteRecursively()

    fun cleanupNamespace(namespace: String): Boolean =
        File(root, sanitize(namespace)).deleteRecursively()

    fun cleanupAll(): Boolean {
        if (!root.exists()) return true
        return root.deleteRecursively()
    }

    fun totalSizeBytes(): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val ROOT_NAME = "oc-workspaces"
        const val NS_PDF = "pdf"
        const val NS_MEDIA = "media"
        const val NS_OFFICE = "office"
        const val NS_ARCHIVE = "archive"

        private fun sanitize(value: String): String =
            value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "x" }
    }
}
