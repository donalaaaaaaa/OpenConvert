package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.FileFormat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * 压缩包引擎（Phase 7）：Apache Commons Compress。
 * - 多文件 → ZIP / TAR / 7Z
 * - 单文件 → GZIP / BZIP2
 * - ZIP / TAR.GZ / TAR.BZ2 / 7Z → 解压到目录
 * 全部流式处理，不整包载入内存。
 */
class ArchiveConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    /**
     * 压缩：inputUris 打包为 targetFormat（ZIP/TAR/GZIP/BZIP2）。
     * GZIP/BZIP2 仅支持单文件输入。
     */
    suspend fun compress(
        inputUris: List<Uri>,
        inputNames: List<String>,
        outputUri: Uri,
        targetFormat: FileFormat,
    ): ConversionResult = withContext(Dispatchers.IO) {
        if (inputUris.isEmpty() || inputNames.size != inputUris.size) {
            return@withContext ConversionResult.Failure("没有可压缩的文件")
        }
        if (targetFormat in setOf(FileFormat.GZIP, FileFormat.BZIP2) && inputUris.size > 1) {
            return@withContext ConversionResult.Failure("${targetFormat.displayName} 只支持单个文件压缩")
        }
        try {
            when (targetFormat) {
                FileFormat.ZIP -> writeZip(inputUris, inputNames, outputUri)
                FileFormat.TAR -> writeTar(inputUris, inputNames, outputUri)
                FileFormat.SEVEN_Z -> writeSevenZ(inputUris, inputNames, outputUri)
                FileFormat.GZIP -> writeSingleStream(inputUris.first(), inputNames.first(), outputUri) {
                    GzipCompressorOutputStream(it)
                }
                FileFormat.BZIP2 -> writeSingleStream(inputUris.first(), inputNames.first(), outputUri) {
                    BZip2CompressorOutputStream(it)
                }
                else -> return@withContext ConversionResult.Failure("不支持的压缩格式")
            }
            onProgress(100)
            val size = resolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L
            ConversionResult.Success(
                outputUri.toString(),
                size.coerceAtLeast(0),
                EngineType.COMMONS_COMPRESS,
            )
        } catch (cancelled: CancellationException) {
            runCatching { resolver.delete(outputUri, null, null) }
            ConversionResult.Cancelled
        } catch (error: Throwable) {
            runCatching { resolver.delete(outputUri, null, null) }
            ConversionResult.Failure(error.toUserMessage("压缩失败，请检查文件是否可读"), error)
        }
    }

    /** 解压 ZIP / TAR.GZ / TAR.BZ2 到输出目录。 */
    suspend fun extract(
        inputUri: Uri,
        outputDirectory: DocumentFile,
        sourceName: String,
    ): ConversionResult = withContext(Dispatchers.IO) {
        try {
            val lower = sourceName.lowercase()
            when {
                lower.endsWith(".zip") -> extractZip(inputUri, outputDirectory)
                lower.endsWith(".7z") -> extractSevenZ(inputUri, outputDirectory)
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> extractTar(inputUri, outputDirectory, gzip = true)
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> extractTar(inputUri, outputDirectory, gzip = false)
                else -> return@withContext ConversionResult.Failure("仅支持 ZIP / 7Z / TAR.GZ / TAR.BZ2 解压")
            }
            onProgress(100)
            ConversionResult.Success(
                outputDirectory.uri.toString(),
                0L,
                EngineType.COMMONS_COMPRESS,
            )
        } catch (cancelled: CancellationException) {
            ConversionResult.Cancelled
        } catch (error: Throwable) {
            ConversionResult.Failure(error.toUserMessage("解压失败，请检查压缩包是否损坏"), error)
        }
    }

    private suspend fun writeZip(inputUris: List<Uri>, inputNames: List<String>, outputUri: Uri) {
        resolver.openOutputStream(outputUri, "w")?.use { raw ->
            ZipArchiveOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.setUseZip64(Zip64Mode.AsNeeded)
                inputUris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val entryName = inputNames[index].substringAfterLast('/')
                    zip.putArchiveEntry(ZipArchiveEntry(entryName))
                    copyResolverStream(uri, zip, entryName)
                    zip.closeArchiveEntry()
                    onProgress(((index + 1) * 90 / inputUris.size).coerceAtLeast(1))
                }
            }
        } ?: throw FileNotFoundException("无法写入压缩包")
    }

    private suspend fun writeTar(inputUris: List<Uri>, inputNames: List<String>, outputUri: Uri) {
        resolver.openOutputStream(outputUri, "w")?.use { raw ->
            TarArchiveOutputStream(BufferedOutputStream(raw)).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                inputUris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val entryName = inputNames[index].substringAfterLast('/')
                    val entry = TarArchiveEntry(entryName)
                    entry.size = resolver.openAssetFileDescriptor(uri, "r")?.length ?: 0L
                    tar.putArchiveEntry(entry)
                    copyResolverStream(uri, tar, entryName)
                    tar.closeArchiveEntry()
                    onProgress(((index + 1) * 90 / inputUris.size).coerceAtLeast(1))
                }
            }
        } ?: throw FileNotFoundException("无法写入压缩包")
    }

    private suspend fun writeSevenZ(inputUris: List<Uri>, inputNames: List<String>, outputUri: Uri) {
        val temps = com.openconvert.app.domain.work.TempWorkspaceManager(context)
        val pack = temps.file(
            com.openconvert.app.domain.work.TempWorkspaceManager.NS_ARCHIVE,
            "pack_${System.currentTimeMillis()}.7z",
        )
        try {
            SevenZOutputFile(pack).use { seven ->
                inputUris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val entryName = inputNames[index].substringAfterLast('/').ifBlank { "file_$index" }
                    val entry = SevenZArchiveEntry().apply {
                        name = entryName
                        size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                    }
                    seven.putArchiveEntry(entry)
                    copyResolverStream(uri, object : java.io.OutputStream() {
                        override fun write(b: Int) {
                            seven.write(byteArrayOf(b.toByte()))
                        }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            seven.write(b, off, len)
                        }
                    }, entryName)
                    seven.closeArchiveEntry()
                    onProgress(((index + 1) * 90 / inputUris.size).coerceAtLeast(1))
                }
            }
            resolver.openOutputStream(outputUri, "w")?.use { out ->
                pack.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
            } ?: throw FileNotFoundException("无法写入 7Z")
        } finally {
            pack.delete()
        }
    }

    private suspend fun writeSingleStream(
        inputUri: Uri,
        inputName: String,
        outputUri: Uri,
        wrap: (BufferedOutputStream) -> java.io.OutputStream,
    ) {
        resolver.openOutputStream(outputUri, "w")?.use { raw ->
            val buffered = BufferedOutputStream(raw)
            wrap(buffered).use { compressor ->
                copyResolverStream(inputUri, compressor, inputName)
            }
            buffered.flush()
        } ?: throw FileNotFoundException("无法写入压缩文件")
    }

    private suspend fun extractZip(inputUri: Uri, directory: DocumentFile) {
        resolver.openInputStream(inputUri)?.use { stream ->
            ZipArchiveInputStream(BufferedInputStream(stream), "UTF-8").use { zip ->
                var index = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name.isBlank()) continue
                    val target = directory.createFile(FileFormat.fromFileName(name).mimeType, name)
                        ?: throw IOException("无法创建解压文件 $name")
                    writeTo(target.uri, zip)
                    index++
                    onProgress(((index + 1) * 90 / 100).coerceAtMost(90))
                }
            }
        } ?: throw FileNotFoundException("无法读取压缩包")
    }

    private suspend fun extractSevenZ(inputUri: Uri, directory: DocumentFile) {
        val temps = com.openconvert.app.domain.work.TempWorkspaceManager(context)
        val pack = temps.file(
            com.openconvert.app.domain.work.TempWorkspaceManager.NS_ARCHIVE,
            "extract_${System.currentTimeMillis()}.7z",
        )
        try {
            resolver.openInputStream(inputUri)?.use { input ->
                pack.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            } ?: throw FileNotFoundException("无法读取压缩包")
            SevenZFile.builder().setFile(pack).get().use { seven ->
                var index = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = seven.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    if (name.isBlank() || name.contains("..")) continue
                    val target = directory.createFile(FileFormat.fromFileName(name).mimeType, name)
                        ?: throw IOException("无法创建解压文件 $name")
                    seven.getInputStream(entry).use { stream -> writeTo(target.uri, stream) }
                    index++
                    onProgress(((index + 1) * 90 / 100).coerceAtMost(90))
                }
            }
        } finally {
            pack.delete()
        }
    }

    private suspend fun extractTar(inputUri: Uri, directory: DocumentFile, gzip: Boolean) {
        resolver.openInputStream(inputUri)?.use { stream ->
            val buffered = BufferedInputStream(stream)
            val decompressed = if (gzip) {
                GzipCompressorInputStream(buffered)
            } else {
                BZip2CompressorInputStream(buffered)
            }
            decompressed.use { decompressor ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(decompressor).use { tar ->
                    var index = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = tar.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name.substringAfterLast('/')
                        if (name.isBlank()) continue
                        val target = directory.createFile(FileFormat.fromFileName(name).mimeType, name)
                            ?: throw IOException("无法创建解压文件 $name")
                        writeTo(target.uri, tar)
                        index++
                        if (index % 5 == 0) onProgress((index % 100).coerceAtLeast(1))
                    }
                }
            }
        } ?: throw FileNotFoundException("无法读取压缩包")
    }

    /** 写入输出：content:// 走 ContentResolver，file:// 走 File（测试/缓存目录）。 */
    private suspend fun writeTo(targetUri: Uri, input: java.io.InputStream) {
        val output: java.io.OutputStream = when (targetUri.scheme) {
            "file" -> java.io.FileOutputStream(java.io.File(targetUri.path!!))
            else -> resolver.openOutputStream(targetUri, "w")
                ?: throw FileNotFoundException("无法写入解压文件")
        }
        output.use { input.copyTo(it, BUFFER_SIZE) }
    }

    private suspend fun copyResolverStream(inputUri: Uri, output: java.io.OutputStream, name: String) {
        resolver.openInputStream(inputUri)?.use { input ->
            input.copyTo(output, BUFFER_SIZE)
        } ?: throw FileNotFoundException("无法读取文件 $name")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
