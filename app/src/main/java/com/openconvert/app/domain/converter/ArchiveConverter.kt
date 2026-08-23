package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.error.ConversionException
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.FileTypeDetector
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
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

/**
 * 压缩包引擎（Phase 7）：Apache Commons Compress。
 * - 多文件 → ZIP / TAR / 7Z
 * - 单文件 → GZIP / BZIP2 / XZ
 * - ZIP / TAR / TAR.GZ / TAR.BZ2 / TAR.XZ / 7Z / GZIP / BZIP2 / XZ → 解压
 * 全部流式处理，不整包载入内存。
 */
class ArchiveConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
    private val policy: ArchiveExtractionPolicy = ArchiveExtractionPolicy.forUsableSpace(
        context.cacheDir.usableSpace,
    ),
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
        if (targetFormat in setOf(FileFormat.GZIP, FileFormat.BZIP2, FileFormat.XZ) && inputUris.size > 1) {
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
                FileFormat.XZ -> writeSingleStream(inputUris.first(), inputNames.first(), outputUri) {
                    XZCompressorOutputStream(it)
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

    /** 解压 ZIP / TAR / TAR.GZ / TAR.BZ2 / TAR.XZ / 7Z / GZIP / BZIP2 / XZ 到输出目录。 */
    suspend fun extract(
        inputUri: Uri,
        outputDirectory: DocumentFile,
        sourceName: String,
    ): ConversionResult = withContext(Dispatchers.IO) {
        val created = mutableListOf<DocumentFile>()
        try {
            rejectForgedExtension(inputUri, sourceName)
            val session = ArchiveExtractionSession(policy)
            val lower = sourceName.lowercase()
            when {
                lower.endsWith(".zip") -> extractZip(inputUri, outputDirectory, session, created)
                lower.endsWith(".7z") -> extractSevenZ(inputUri, outputDirectory, session, created)
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ->
                    extractTar(inputUri, outputDirectory, CompressKind.GZIP, session, created)
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") ->
                    extractTar(inputUri, outputDirectory, CompressKind.BZIP2, session, created)
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") ->
                    extractTar(inputUri, outputDirectory, CompressKind.XZ, session, created)
                lower.endsWith(".tar") -> extractTar(inputUri, outputDirectory, CompressKind.NONE, session, created)
                lower.endsWith(".gz") -> extractSingle(inputUri, outputDirectory, sourceName, CompressKind.GZIP, session, created)
                lower.endsWith(".bz2") -> extractSingle(inputUri, outputDirectory, sourceName, CompressKind.BZIP2, session, created)
                lower.endsWith(".xz") -> extractSingle(inputUri, outputDirectory, sourceName, CompressKind.XZ, session, created)
                else -> return@withContext ConversionResult.Failure(
                    "仅支持 ZIP / 7Z / TAR / TAR.GZ / TAR.BZ2 / TAR.XZ / GZIP / BZIP2 / XZ 解压",
                )
            }
            if (session.entryCount == 0) {
                throw ConversionException.InvalidFile("压缩包为空或已损坏")
            }
            onProgress(100)
            ConversionResult.Success(
                outputDirectory.uri.toString(),
                0L,
                EngineType.COMMONS_COMPRESS,
            )
        } catch (cancelled: CancellationException) {
            deleteCreated(created)
            ConversionResult.Cancelled
        } catch (error: ConversionException) {
            deleteCreated(created)
            ConversionResult.Failure(error.userFriendlyMessage, error, error.code.name)
        } catch (error: Throwable) {
            deleteCreated(created)
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

    private suspend fun extractZip(
        inputUri: Uri,
        directory: DocumentFile,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ) {
        resolver.openInputStream(inputUri)?.use { stream ->
            ZipArchiveInputStream(BufferedInputStream(stream), "UTF-8").use { zip ->
                var index = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    writeEntry(
                        directory = directory,
                        rawName = entry.name,
                        input = zip,
                        compressedSize = entry.compressedSize,
                        uncompressedSize = entry.size,
                        session = session,
                        created = created,
                    )
                    index++
                    onProgress(((index + 1) * 90 / 100).coerceAtMost(90))
                }
            }
        } ?: throw FileNotFoundException("无法读取压缩包")
    }

    private suspend fun extractSevenZ(
        inputUri: Uri,
        directory: DocumentFile,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ) {
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
                    seven.getInputStream(entry).use { stream ->
                        writeEntry(
                            directory = directory,
                            rawName = entry.name,
                            input = stream,
                            compressedSize = -1L,
                            uncompressedSize = entry.size,
                            session = session,
                            created = created,
                        )
                    }
                    index++
                    onProgress(((index + 1) * 90 / 100).coerceAtMost(90))
                }
            }
        } finally {
            pack.delete()
        }
    }

    private suspend fun extractTar(
        inputUri: Uri,
        directory: DocumentFile,
        wrap: CompressKind,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ) {
        resolver.openInputStream(inputUri)?.use { stream ->
            wrapStream(BufferedInputStream(stream), wrap).use { decompressed ->
                org.apache.commons.compress.archivers.tar.TarArchiveInputStream(decompressed).use { tar ->
                    var index = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = tar.nextEntry ?: break
                        if (entry.isDirectory) continue
                        writeEntry(
                            directory = directory,
                            rawName = entry.name,
                            input = tar,
                            compressedSize = -1L,
                            uncompressedSize = entry.size,
                            session = session,
                            created = created,
                        )
                        index++
                        if (index % 5 == 0) onProgress((index % 100).coerceAtLeast(1))
                    }
                }
            }
        } ?: throw FileNotFoundException("无法读取压缩包")
    }

    private suspend fun extractSingle(
        inputUri: Uri,
        directory: DocumentFile,
        sourceName: String,
        wrap: CompressKind,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ) {
        val outName = sourceName
            .substringAfterLast('/')
            .removeSuffix(".gz")
            .removeSuffix(".bz2")
            .removeSuffix(".xz")
            .ifBlank { "extracted" }
        val compressedHint = resolver.openFileDescriptor(inputUri, "r")?.use { it.statSize } ?: -1L
        resolver.openInputStream(inputUri)?.use { stream ->
            wrapStream(BufferedInputStream(stream), wrap).use { input ->
                writeEntry(
                    directory = directory,
                    rawName = outName,
                    input = input,
                    compressedSize = compressedHint,
                    uncompressedSize = -1L,
                    session = session,
                    created = created,
                )
            }
        } ?: throw FileNotFoundException("无法读取压缩文件")
    }

    private fun wrapStream(input: BufferedInputStream, wrap: CompressKind): java.io.InputStream = when (wrap) {
        CompressKind.NONE -> input
        CompressKind.GZIP -> GzipCompressorInputStream(input)
        CompressKind.BZIP2 -> BZip2CompressorInputStream(input)
        CompressKind.XZ -> XZCompressorInputStream(input)
    }

    private suspend fun writeEntry(
        directory: DocumentFile,
        rawName: String,
        input: java.io.InputStream,
        compressedSize: Long,
        uncompressedSize: Long,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ) {
        when (val decision = session.decidePath(rawName)) {
            ArchivePathDecision.Skip -> return
            is ArchivePathDecision.Reject -> throw ConversionException.ArchiveExpansionLimit(decision.reason)
            is ArchivePathDecision.Accept -> {
                session.beginEntry(compressedSize, uncompressedSize)?.let { reason ->
                    throw ConversionException.ArchiveExpansionLimit(reason)
                }
                val target = createNestedFile(directory, decision, session, created)
                writeLimited(target.uri, input, session, compressedSize)
            }
        }
    }

    private fun createNestedFile(
        root: DocumentFile,
        decision: ArchivePathDecision.Accept,
        session: ArchiveExtractionSession,
        created: MutableList<DocumentFile>,
    ): DocumentFile {
        val fileRoot = root.uri.takeIf { it.scheme == "file" }?.path?.let { java.io.File(it) }
        if (fileRoot != null) {
            var dir = fileRoot
            for (segment in decision.directories) {
                dir = java.io.File(dir, segment)
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IOException("无法创建解压目录 $segment")
                }
                if (!dir.isDirectory) {
                    throw ConversionException.InvalidFile("压缩包路径与已有文件冲突: $segment")
                }
            }
            val name = session.uniqueName(decision.fileName, dir.list()?.toSet().orEmpty())
            val dest = java.io.File(dir, name)
            if (!dest.exists() && !dest.createNewFile()) {
                throw IOException("无法创建解压文件 $name")
            }
            val document = DocumentFile.fromFile(dest)
            created += document
            return document
        }
        var dir = root
        for (segment in decision.directories) {
            val existing = dir.findFile(segment)
            dir = when {
                existing == null -> {
                    val made = dir.createDirectory(segment)
                        ?: throw IOException("无法创建解压目录 $segment")
                    created += made
                    made
                }
                existing.isDirectory -> existing
                else -> throw ConversionException.InvalidFile("压缩包路径与已有文件冲突: $segment")
            }
        }
        val existingNames = dir.listFiles()?.mapNotNull { it.name }?.toSet().orEmpty()
        val name = session.uniqueName(decision.fileName, existingNames)
        val target = dir.createFile(mimeForExtractedName(name), name)
            ?: throw IOException("无法创建解压文件 $name")
        created += target
        return target
    }

    private fun mimeForExtractedName(@Suppress("UNUSED_PARAMETER") name: String): String {
        // RawDocumentFile 会按 MIME 补后缀（image/jpeg → .jpeg），所以解压一律用无映射类型。
        return "application/x.openconvert.extract"
    }

    private suspend fun writeLimited(
        targetUri: Uri,
        input: java.io.InputStream,
        session: ArchiveExtractionSession?,
        compressedSize: Long,
    ) {
        val output: java.io.OutputStream = when (targetUri.scheme) {
            "file" -> java.io.FileOutputStream(java.io.File(targetUri.path!!))
            else -> resolver.openOutputStream(targetUri, "w")
                ?: throw FileNotFoundException("无法写入解压文件")
        }
        output.use { dest ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (session != null) {
                    session.recordWritten(read, compressedSize)?.let { reason ->
                        throw ConversionException.ArchiveExpansionLimit(reason)
                    }
                }
                dest.write(buffer, 0, read)
            }
        }
    }

    private fun rejectForgedExtension(inputUri: Uri, sourceName: String) {
        val named = FileFormat.fromFileName(sourceName)
        if (named.category != FileCategory.ARCHIVE) return
        val magic = resolver.openInputStream(inputUri)?.use { FileTypeDetector.fromMagicNumber(it) }
            ?: return
        val nonArchive = magic.category == FileCategory.IMAGE ||
            magic.category == FileCategory.AUDIO ||
            magic.category == FileCategory.VIDEO ||
            magic.category == FileCategory.PDF
        if (nonArchive) {
            throw ConversionException.InvalidFile("扩展名与文件内容不符")
        }
    }

    private fun deleteCreated(created: List<DocumentFile>) {
        created.asReversed().forEach { file -> runCatching { file.delete() } }
    }

    private suspend fun copyResolverStream(inputUri: Uri, output: java.io.OutputStream, name: String) {
        resolver.openInputStream(inputUri)?.use { input ->
            input.copyTo(output, BUFFER_SIZE)
        } ?: throw FileNotFoundException("无法读取文件 $name")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }

    private enum class CompressKind { NONE, GZIP, BZIP2, XZ }
}
