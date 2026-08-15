package com.openconvert.app.domain.converter

import android.content.Context
import android.util.Log
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office

/**
 * Office → PDF 转换器（LibreOfficeKit，可选下载包）。
 *
 * 引擎可用性：`OfficeEngine.isAvailable()`——首次调用尝试加载
 * `liblo-native-code.so` 与 NSS 配套库；库不存在（Office Pack 未下载）
 * 时返回 false，调用方给出"需要下载 Office 支持包"的提示。
 *
 * 初始化序列（真机验证，见 docs/lokit-validation.md）：
 * 1. 将 assets/unpack 复制到 applicationInfo.dataDir
 * 2. 按序加载 NSS 库链 + lo-native-code（LibreOfficeKit 静态块）
 * 3. putenv(SAL_LOG) + LibreOfficeKit.init
 * 4. documentLoad → saveAs("pdf")
 *
 * LOKit 是进程级单例，初始化一次；documentLoad/saveAs 需串行执行。
 */
class OfficeConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) : Converter {

    override fun supports(inputFormat: FileFormat, outputFormat: FileFormat): Boolean =
        inputFormat.category == FileCategory.OFFICE && outputFormat == FileFormat.PDF

    override suspend fun convert(task: ConversionTask): ConversionResult = withContext(Dispatchers.IO) {
        val outputUri = task.outputUri?.let { android.net.Uri.parse(it) }
            ?: return@withContext ConversionResult.Failure("没有选择输出文件")
        if (!supports(task.sourceFormat, task.targetFormat)) {
            return@withContext ConversionResult.Failure(
                "暂不支持 ${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            )
        }
        if (!OfficeEngine.isAvailable(context)) {
            return@withContext ConversionResult.Failure(
                "Office 转换引擎不可用，请检查运行环境",
            )
        }
        try {
            reportProgress(5)
            // ensureInitialized 内部：先 unpack → 再加载库 → 再 init（顺序与真机验证一致）
            OfficeEngine.ensureInitialized(context)

            val workDir = File(context.cacheDir, "office-conversions/${task.id}")
            if (!workDir.mkdirs() && !workDir.isDirectory) {
                throw java.io.IOException("无法创建 Office 转换临时目录")
            }
            val inputFile = File(workDir, "input.${task.sourceFormat.preferredExtension}")
            context.contentResolver.openInputStream(android.net.Uri.parse(task.sourceUri))?.use { input ->
                FileOutputStream(inputFile).use { output -> input.copyTo(output) }
            } ?: throw java.io.FileNotFoundException("无法读取源文件")

            reportProgress(30)
            coroutineContext.ensureActive()
            val pdfFile = File(workDir, "output.pdf")
            val office = OfficeEngine.office
            val document = office.documentLoad(inputFile.absolutePath)
                ?: throw java.io.IOException(
                    "LibreOffice 无法打开该文档（可能已损坏或格式不支持）: ${office.getError()}",
                )
            try {
                document.initializeForRendering()
                reportProgress(70)
                coroutineContext.ensureActive()
                document.saveAs(pdfFile.absolutePath, "pdf", "")
            } finally {
                runCatching { document.destroy() }
            }

            if (!pdfFile.isFile || pdfFile.length() <= 0L) {
                throw java.io.IOException("PDF 生成失败，未产生有效文件")
            }

            reportProgress(90)
            coroutineContext.ensureActive()
            context.contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                pdfFile.inputStream().use { input ->
                    com.openconvert.app.domain.work.BoundedIo.copy(input, output)
                }
            } ?: throw java.io.FileNotFoundException("无法写入目标文件")
            val outputSize = pdfFile.length()
            reportProgress(100)
            ConversionResult.Success(outputUri.toString(), outputSize)
        } catch (cancelled: CancellationException) {
            runCatching { context.contentResolver.delete(outputUri, null, null) }
            throw cancelled
        } catch (error: Throwable) {
            runCatching { context.contentResolver.delete(outputUri, null, null) }
            ConversionResult.Failure(error.toUserMessage("Office 转换失败，请检查文件是否损坏"), error)
        }
    }

    private suspend fun reportProgress(progress: Int) {
        coroutineContext.ensureActive()
        onProgress(progress)
    }
}

/**
 * LibreOfficeKit 引擎门面：库加载探测 + 进程级单例初始化。
 * 库来源优先级：APK 内置（开发/验证）→ Office Pack 下载目录（生产）。
 */
object OfficeEngine {
    private const val TAG = "OpenConvert.LOKit"

    @Volatile
    private var loadState: Int = STATE_UNTRIED // 0 untried, 1 ok, 2 failed
    @Volatile
    private var initialized = false
    @Volatile
    private var _office: Office? = null

    val office: Office
        get() = checkNotNull(_office) { "LOKit 未初始化" }

    /** 引擎库是否已打包/可加载。 */
    fun isAvailable(context: Context? = null): Boolean {
        if (loadState == STATE_OK) return true
        if (loadState == STATE_FAILED) return false
        synchronized(this) {
            if (loadState != STATE_UNTRIED) return loadState == STATE_OK
            loadState = try {
                if (context != null && OfficePackManager.isInstalled(context)) {
                    PackLibraryLoader(context).prepare()
                }
                LibreOfficeKit.initializeLibrary()
                STATE_OK
            } catch (t: Throwable) {
                Log.w(TAG, "LOKit 库不可用（Office Pack 未下载？）: $t")
                STATE_FAILED
            }
            return loadState == STATE_OK
        }
    }

    /** 进程级单例初始化（幂等）。 */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            // 1. 先 unpack → 应用数据根目录（必须在库加载/init 之前）
            //    复制三部分到 dataDir：unpack/（program+user+etc）、share/、program/。
            //    注意：lo-bootstrap 的 zip 解析器只兼容 AGP 打包的 APK zip；
            //    第三方生成的 pack zip 可能无法被 mmap 读取，因此资源全部落到 dataDir。
            val dataDir = File(appContext.applicationInfo.dataDir)
            dataDir.mkdirs()
            val marker = File(dataDir, ".lokit-unpacked-v4")
            if (!marker.exists()) {
                val packAssets = OfficePackManager.assetsDir(appContext)
                val packUnpack = File(packAssets, "unpack")
                val copied = if (packUnpack.isDirectory) {
                    copyFileDir(packUnpack, dataDir)
                } else {
                    copyAssetDir(appContext.assets, "unpack", dataDir)
                }
                val packShare = File(packAssets, "share")
                if (packShare.isDirectory) {
                    copyFileDir(packShare, File(dataDir, "share"))
                } else {
                    copyAssetDir(appContext.assets, "share", File(dataDir, "share"))
                }
                val packProgram = File(packAssets, "program")
                if (packProgram.isDirectory) {
                    copyFileDir(packProgram, File(dataDir, "program"))
                } else {
                    copyAssetDir(appContext.assets, "program", File(dataDir, "program"))
                }
                Log.i(TAG, "unpacked $copied files + share/program to ${dataDir.absolutePath}")
                marker.writeText("1")
            }
            // 2. 加载库并初始化
            if (!isAvailable(appContext)) throw IllegalStateException("LOKit 库不可用")
            LibreOfficeKit.putenv("SAL_LOG=+WARN+INFO")
            LibreOfficeKit.init(appContext)
            val handle = LibreOfficeKit.getLibreOfficeKitHandle()
                ?: throw IllegalStateException("LOKit 初始化失败：handle 为空")
            _office = Office(handle)
            initialized = true
            Log.i(TAG, "LOKit 初始化完成")
        }
    }

    /** 卸载 Office Pack 后重置状态。 */
    fun reset() {
        synchronized(this) {
            loadState = STATE_UNTRIED
            initialized = false
            _office = null
        }
    }

    private fun copyAssetDir(assets: android.content.res.AssetManager, path: String, target: File): Int {
        var count = 0
        val children = assets.list(path) ?: return 0
        for (child in children) {
            val childPath = "$path/$child"
            val dest = File(target, child)
            try {
                assets.open(childPath).use { input ->
                    dest.parentFile?.mkdirs()
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                count++
            } catch (_: Exception) {
                count += copyAssetDir(assets, childPath, dest)
            }
        }
        return count
    }

    private fun copyFileDir(source: File, target: File): Int {
        var count = 0
        source.listFiles()?.forEach { child ->
            val dest = File(target, child.name)
            if (child.isDirectory) {
                count += copyFileDir(child, dest)
            } else {
                dest.parentFile?.mkdirs()
                child.copyTo(dest, overwrite = true)
                count++
            }
        }
        return count
    }

    private const val STATE_UNTRIED = 0
    private const val STATE_OK = 1
    private const val STATE_FAILED = 2
}

/** 从 Office Pack 目录加载库（生产路径）。
 * 先 System.load(绝对路径) 预加载全部库；之后 LibreOfficeKit 静态块的
 * System.loadLibrary("nspr4") 等调用会发现同 soname 已加载而跳过。
 */
private class PackLibraryLoader(private val context: Context) {
    fun prepare() {
        val libsDir = OfficePackManager.libsDir(context)
        val abiDir = File(libsDir, android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a")
        if (!abiDir.isDirectory) {
            throw IllegalStateException("Office Pack 缺少 ${abiDir.name} 库目录")
        }
        // 复制到应用私有 native 目录（内存映射需要可读路径）
        val dest = File(context.filesDir, "native")
        dest.mkdirs()
        val libs = abiDir.listFiles()?.filter { it.name.endsWith(".so") } ?: emptyList()
        if (libs.isEmpty()) throw IllegalStateException("Office Pack 缺少 .so 文件")
        libs.forEach { so ->
            val target = File(dest, so.name)
            if (!target.exists() || target.length() != so.length()) {
                so.copyTo(target, overwrite = true)
            }
        }
        // 按 NSS 链顺序预加载（必须保持顺序）
        // 注意：libc++_shared.so 不预加载——若主 APK 已有 FFmpeg 的 libc++_shared，
        // System.load 会因 soname 相同而跳过 pack 版，导致 LO 与 FFmpeg 的 libc++ ABI 混用。
        // 这里依赖 LO 的 libc++ 需求与 FFmpeg 版兼容（NDK r2x 均提供 __ndk1 ABI）。
        val order = listOf(
            "libnspr4.so", "libplds4.so", "libplc4.so", "libnssutil3.so",
            "libfreebl3.so", "libsqlite3.so", "libsoftokn3.so", "libnss3.so",
            "libnssckbi.so", "libnssdbm3.so", "libsmime3.so", "libssl3.so",
            "liblo-native-code.so",
        )
        order.forEach { name ->
            val lib = File(dest, name)
            if (lib.isFile) {
                System.load(lib.absolutePath)
            }
        }
        Log.i("OpenConvert.LOKit", "pack libs loaded from ${dest.absolutePath}")
    }
}
