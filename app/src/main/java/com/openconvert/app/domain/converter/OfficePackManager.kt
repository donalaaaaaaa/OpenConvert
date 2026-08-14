package com.openconvert.app.domain.converter

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Office Pack 可选下载管理器（计划书 §六十二：Office 作为大型可选模块）。
 *
 * 设计：
 * - 基础 APK **不包含** LibreOfficeKit 库与资源（保持 ~35MB）
 * - 用户在设置页下载 Office Pack（zip 包：lib/ 下的 .so + assets/ 下的资源）
 * - 下载后解压到 `filesDir/office-pack/`，运行时通过 [OfficeEngine] 动态加载
 *
 * 产物结构（office-pack.zip）：
 * ```
 * lib/arm64-v8a/liblo-native-code.so   (+ 13 个 NSS/c++ 库)
 * assets/program/...   assets/share/...   assets/unpack/...
 * ```
 */
object OfficePackManager {
    private const val TAG = "OpenConvert.OfficePack"
    const val PACK_VERSION = 1

    fun packDir(context: Context): File = File(context.filesDir, "office-pack")

    /** 安装时保留的 pack zip 副本（LOKit mmap 读取 assets 用）。 */
    fun packZipPath(context: Context): String? {
        val zip = File(packDir(context), "office-pack.zip")
        return if (zip.isFile) zip.absolutePath else null
    }

    fun libsDir(context: Context): File = File(packDir(context), "lib")

    fun assetsDir(context: Context): File = File(packDir(context), "assets")

    /** Office Pack 是否已安装（目录存在且含核心库）。 */
    fun isInstalled(context: Context): Boolean {
        val abiDir = File(libsDir(context), android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a")
        val core = File(abiDir, "liblo-native-code.so")
        return core.isFile && core.length() > 10_000_000L
    }

    /**
     * 解压 Office Pack zip 到 filesDir/office-pack。
     * zip 内路径：lib/<abi>/xxx.so 与 assets/...。
     */
    fun install(context: Context, packZip: File): Result<Unit> = runCatching {
        val root = packDir(context)
        root.mkdirs()
        ZipInputStream(packZip.inputStream().buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name
                    // 保留顶层目录结构：lib/<abi>/xxx.so 与 assets/...
                    val rel = when {
                        name.startsWith("lib/") || name.startsWith("assets/") -> name
                        else -> null
                    }
                    if (rel != null) {
                        val target = File(root, rel)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zip.copyTo(out, 64 * 1024) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        check(isInstalled(context)) { "解压后未找到核心库" }
        // 保留 zip 副本供 LOKit mmap 读取 assets/ 条目
        val zipCopy = File(packDir(context), "office-pack.zip")
        if (!zipCopy.isFile || zipCopy.length() != packZip.length()) {
            packZip.copyTo(zipCopy, overwrite = true)
        }
        OfficeEngine.reset() // 清除失败缓存，下次 isAvailable 重新探测
        Log.i(TAG, "Office Pack 安装完成: ${packDir(context).absolutePath}")
    }

    /** 删除 Office Pack（释放空间）。 */
    fun uninstall(context: Context) {
        packDir(context).deleteRecursively()
        OfficeEngine.reset()
    }

    fun packSizeMb(): Long = 180
}
