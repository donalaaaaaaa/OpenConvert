package com.openconvert.app.domain.converter

import android.content.Context
import android.util.Log
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.work.TempWorkspaceManager
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
 * Office → PDF 转换器（LibreOfficeKit，Office 发行版内置）。
 *
 * 引擎可用性：`OfficeEngine.isAvailable()`——首次调用尝试加载
 * `liblo-native-code.so` 与 NSS 配套库；轻量版未打包这些文件，因此返回 false。
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
                "当前安装包未内置 Office 转换引擎，请安装 Office 版",
            )
        }
        try {
            reportProgress(5)
            // ensureInitialized 内部：先 unpack → 再加载库 → 再 init（顺序与真机验证一致）
            OfficeEngine.ensureInitialized(context)

            val workDir = TempWorkspaceManager(context).directory(TempWorkspaceManager.NS_OFFICE, task.id)
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
            ConversionResult.Success(outputUri.toString(), outputSize, EngineType.LIBREOFFICE_KIT)
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
 * 只从 Office Flavor 的 APK 内置库加载。filesDir 动态 Pack 已确认在 Android 16
 * 上会 SIGABRT，正式运行时不再探测、不再解压。
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

    /** 引擎库是否已打包/可加载。只看 APK nativeLibraryDir / System.loadLibrary。 */
    fun isAvailable(context: Context? = null): Boolean {
        if (loadState == STATE_OK) return true
        if (loadState == STATE_FAILED) return false
        synchronized(this) {
            if (loadState != STATE_UNTRIED) return loadState == STATE_OK
            loadState = try {
                LibreOfficeKit.initializeLibrary()
                STATE_OK
            } catch (t: Throwable) {
                Log.w(TAG, "LOKit 库不可用（当前安装包未内置 Office 引擎）: $t")
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
            val dataDir = File(appContext.applicationInfo.dataDir)
            dataDir.mkdirs()
            val marker = File(dataDir, ".lokit-unpacked-v4")
            if (!marker.exists()) {
                val copied = copyAssetDir(appContext.assets, "unpack", dataDir)
                copyAssetDir(appContext.assets, "share", File(dataDir, "share"))
                copyAssetDir(appContext.assets, "program", File(dataDir, "program"))
                Log.i(TAG, "unpacked $copied files + share/program to ${dataDir.absolutePath}")
                marker.writeText("1")
            }
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

    private const val STATE_UNTRIED = 0
    private const val STATE_OK = 1
    private const val STATE_FAILED = 2
}

