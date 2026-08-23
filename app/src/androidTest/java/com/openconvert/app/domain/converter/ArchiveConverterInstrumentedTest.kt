package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.error.ConversionError
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.FileTypeDetector
import java.io.ByteArrayOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveConverterInstrumentedTest {
    @Test
    fun compressesFilesIntoZip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val first = createTextFile(resolver, "zip-a-$testId.txt", "hello from a")
        val second = createTextFile(resolver, "zip-b-$testId.txt", "hello from b")
        val output = createPendingDownload(resolver, "zip-out-$testId.zip", "application/zip")

        try {
            val result = ArchiveConverter(context).compress(
                inputUris = listOf(first, second),
                inputNames = listOf("zip-a-$testId.txt", "zip-b-$testId.txt"),
                outputUri = output,
                targetFormat = FileFormat.ZIP,
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)

            resolver.openInputStream(output)!!.use { stream ->
                ZipInputStream(stream).use { zip ->
                    val names = mutableListOf<String>()
                    var totalBytes = 0
                    var entry = zip.nextEntry
                    while (entry != null) {
                        names += entry.name
                        val buffer = ByteArrayOutputStream()
                        zip.copyTo(buffer)
                        totalBytes += buffer.size()
                        entry = zip.nextEntry
                    }
                    assertEquals(2, names.size)
                    assertTrue(names.any { it.startsWith("zip-a-") })
                    assertTrue(names.any { it.startsWith("zip-b-") })
                    assertTrue(totalBytes > 0)
                }
            }
        } finally {
            runCatching { resolver.delete(first, null, null) }
            runCatching { resolver.delete(second, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun rejectsMultiFileGzip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val first = createTextFile(resolver, "gz-a-$testId.txt", "a")
        val second = createTextFile(resolver, "gz-b-$testId.txt", "b")
        val output = createPendingDownload(resolver, "gz-out-$testId.gz", "application/gzip")

        try {
            val result = ArchiveConverter(context).compress(
                inputUris = listOf(first, second),
                inputNames = listOf("gz-a-$testId.txt", "gz-b-$testId.txt"),
                outputUri = output,
                targetFormat = FileFormat.GZIP,
            )
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
        } finally {
            runCatching { resolver.delete(first, null, null) }
            runCatching { resolver.delete(second, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun extractsZipIntoTree() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()

        // 先创建一个 ZIP（复用 compress 逻辑）
        val input = createTextFile(resolver, "extract-src-$testId.txt", "content to extract")
        val zip = createPendingDownload(resolver, "extract-in-$testId.zip", "application/zip")
        val compress = ArchiveConverter(context).compress(
            inputUris = listOf(input),
            inputNames = listOf("extract-src-$testId.txt"),
            outputUri = zip,
            targetFormat = FileFormat.ZIP,
        )
        assertTrue("zip creation failed: $compress", compress is ConversionResult.Success)

        // 输出目录：cache 下的临时目录（file:// 走 DocumentFile.fromFile）
        val outputDir = java.io.File(context.cacheDir, "extract-test-$testId")
        outputDir.mkdirs()
        val tree = DocumentFile.fromFile(outputDir)

        try {
            val result = ArchiveConverter(context).extract(
                inputUri = zip,
                outputDirectory = tree,
                sourceName = "extract-in-$testId.zip",
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val extracted = outputDir.listFiles()?.firstOrNull { it.name.startsWith("extract-src-") }
            assertTrue("extracted file missing", extracted != null)
            assertEquals("content to extract", extracted!!.readText())
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(zip, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun compressesAndExtractsSevenZ() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createTextFile(resolver, "seven-src-$testId.txt", "seven-z payload")
        val pack = createPendingDownload(resolver, "seven-out-$testId.7z", "application/x-7z-compressed")
        val outputDir = java.io.File(context.cacheDir, "extract-7z-$testId")
        outputDir.mkdirs()
        try {
            val compress = ArchiveConverter(context).compress(
                inputUris = listOf(input),
                inputNames = listOf("seven-src-$testId.txt"),
                outputUri = pack,
                targetFormat = FileFormat.SEVEN_Z,
            )
            assertTrue("7z compress failed: $compress", compress is ConversionResult.Success)

            resolver.openFileDescriptor(pack, "r")!!.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { stream ->
                    val header = ByteArray(6)
                    assertEquals(6, stream.read(header))
                    assertEquals(
                        FileFormat.SEVEN_Z,
                        FileTypeDetector.fromMagicBytes(header, 6),
                    )
                }
            }

            val extract = ArchiveConverter(context).extract(
                inputUri = pack,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "seven-out-$testId.7z",
            )
            assertTrue("7z extract failed: $extract", extract is ConversionResult.Success)
            val extracted = outputDir.listFiles()?.firstOrNull { it.name.startsWith("seven-src-") }
            assertEquals("seven-z payload", extracted!!.readText())
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(pack, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun compressesAndExtractsXz() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createTextFile(resolver, "xz-src-$testId.txt", "xz payload")
        val pack = createPendingDownload(resolver, "xz-out-$testId.xz", "application/x-xz")
        val outputDir = java.io.File(context.cacheDir, "extract-xz-$testId")
        outputDir.mkdirs()
        try {
            val compress = ArchiveConverter(context).compress(
                inputUris = listOf(input),
                inputNames = listOf("xz-src-$testId.txt"),
                outputUri = pack,
                targetFormat = FileFormat.XZ,
            )
            assertTrue("xz compress failed: $compress", compress is ConversionResult.Success)

            resolver.openFileDescriptor(pack, "r")!!.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { stream ->
                    val header = ByteArray(6)
                    assertEquals(6, stream.read(header))
                    assertEquals(FileFormat.XZ, FileTypeDetector.fromMagicBytes(header, 6))
                }
            }

            val extract = ArchiveConverter(context).extract(
                inputUri = pack,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "xz-out-$testId.xz",
            )
            assertTrue("xz extract failed: $extract", extract is ConversionResult.Success)
            val extracted = outputDir.listFiles()?.firstOrNull { it.name.startsWith("xz-out-") }
            assertEquals("xz payload", extracted!!.readText())
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(pack, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun compressesAndExtractsPlainTar() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createTextFile(resolver, "tar-src-$testId.txt", "plain tar payload")
        val pack = createPendingDownload(resolver, "tar-out-$testId.tar", "application/x-tar")
        val outputDir = java.io.File(context.cacheDir, "extract-tar-$testId")
        outputDir.mkdirs()
        try {
            val compress = ArchiveConverter(context).compress(
                inputUris = listOf(input),
                inputNames = listOf("tar-src-$testId.txt"),
                outputUri = pack,
                targetFormat = FileFormat.TAR,
            )
            assertTrue("tar compress failed: $compress", compress is ConversionResult.Success)
            val extract = ArchiveConverter(context).extract(
                inputUri = pack,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "tar-out-$testId.tar",
            )
            assertTrue("tar extract failed: $extract", extract is ConversionResult.Success)
            val extracted = outputDir.listFiles()?.firstOrNull { it.name.startsWith("tar-src-") }
            assertEquals("plain tar payload", extracted!!.readText())
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(pack, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun compressesAndExtractsGzip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createTextFile(resolver, "gz-src-$testId.txt", "gzip payload")
        val pack = createPendingDownload(resolver, "gz-out-$testId.gz", "application/gzip")
        val outputDir = java.io.File(context.cacheDir, "extract-gz-$testId")
        outputDir.mkdirs()
        try {
            val compress = ArchiveConverter(context).compress(
                inputUris = listOf(input),
                inputNames = listOf("gz-src-$testId.txt"),
                outputUri = pack,
                targetFormat = FileFormat.GZIP,
            )
            assertTrue("gzip compress failed: $compress", compress is ConversionResult.Success)
            val extract = ArchiveConverter(context).extract(
                inputUri = pack,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "gz-out-$testId.gz",
            )
            assertTrue("gzip extract failed: $extract", extract is ConversionResult.Success)
            val extracted = outputDir.listFiles()?.firstOrNull { it.name.startsWith("gz-out-") }
            assertEquals("gzip payload", extracted!!.readText())
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(pack, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun restoresNestedDirectoriesAndKeepsDuplicateBasenames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val zip = createPendingDownload(resolver, "nested-$testId.zip", "application/zip")
        writeZip(resolver, zip) { out ->
            putText(out, "a/config.json", "from-a")
            putText(out, "b/config.json", "from-b")
        }
        val outputDir = java.io.File(context.cacheDir, "extract-nested-$testId")
        outputDir.mkdirs()
        try {
            val result = ArchiveConverter(context).extract(
                inputUri = zip,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "nested-$testId.zip",
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            assertEquals("from-a", java.io.File(outputDir, "a/config.json").readText())
            assertEquals("from-b", java.io.File(outputDir, "b/config.json").readText())
        } finally {
            runCatching { resolver.delete(zip, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun renamesDuplicateEntriesInsteadOfOverwrite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val zip = createPendingDownload(resolver, "dup-$testId.zip", "application/zip")
        writeZip(resolver, zip) { out ->
            putText(out, "photo.jpg", "first")
            putText(out, "photo.jpg", "second")
        }
        val outputDir = java.io.File(context.cacheDir, "extract-dup-$testId")
        outputDir.mkdirs()
        try {
            val result = ArchiveConverter(context).extract(
                inputUri = zip,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "dup-$testId.zip",
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val names = outputDir.list()?.toSet().orEmpty()
            assertTrue(names.contains("photo.jpg"))
            assertTrue(names.contains("photo (1).jpg"))
            assertEquals("first", java.io.File(outputDir, "photo.jpg").readText())
            assertEquals("second", java.io.File(outputDir, "photo (1).jpg").readText())
        } finally {
            runCatching { resolver.delete(zip, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsZipSlipAndLeavesDestinationClean() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val zip = createPendingDownload(resolver, "slip-$testId.zip", "application/zip")
        writeZip(resolver, zip) { out ->
            putText(out, "../../evil-$testId.txt", "pwned")
            putText(out, "ok.txt", "safe")
        }
        val outputDir = java.io.File(context.cacheDir, "extract-slip-$testId")
        outputDir.mkdirs()
        val outside = java.io.File(context.cacheDir, "evil-$testId.txt")
        try {
            val result = ArchiveConverter(context).extract(
                inputUri = zip,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "slip-$testId.zip",
            )
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
            val failure = result as ConversionResult.Failure
            assertEquals(ConversionError.Code.ARCHIVE_EXPANSION_LIMIT.name, failure.errorCode)
            assertTrue(!outside.exists())
            assertTrue(outputDir.walkTopDown().none { it.isFile })
        } finally {
            runCatching { resolver.delete(zip, null, null) }
            runCatching { outside.delete() }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsForgedZipExtension() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val fake = createPendingDownload(resolver, "forged-$testId.zip", "application/zip")
        resolver.openOutputStream(fake, "w")!!.use { stream ->
            stream.write(
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                ),
            )
        }
        val outputDir = java.io.File(context.cacheDir, "extract-forged-$testId")
        outputDir.mkdirs()
        try {
            val result = ArchiveConverter(context).extract(
                inputUri = fake,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "forged-$testId.zip",
            )
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
            val failure = result as ConversionResult.Failure
            assertEquals(ConversionError.Code.INVALID_FILE.name, failure.errorCode)
        } finally {
            runCatching { resolver.delete(fake, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsOverLimitEntriesAndCleansPartialOutput() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val zip = createPendingDownload(resolver, "limit-$testId.zip", "application/zip")
        writeZip(resolver, zip) { out ->
            putText(out, "one.txt", "1")
            putText(out, "two.txt", "2")
            putText(out, "three.txt", "3")
        }
        val outputDir = java.io.File(context.cacheDir, "extract-limit-$testId")
        outputDir.mkdirs()
        val tight = ArchiveExtractionPolicy(
            maxEntries = 2,
            maxSingleFileBytes = 1024,
            maxTotalUncompressedBytes = 1024,
        )
        try {
            val result = ArchiveConverter(context, policy = tight).extract(
                inputUri = zip,
                outputDirectory = DocumentFile.fromFile(outputDir),
                sourceName = "limit-$testId.zip",
            )
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
            assertEquals(
                ConversionError.Code.ARCHIVE_EXPANSION_LIMIT.name,
                (result as ConversionResult.Failure).errorCode,
            )
            assertTrue(outputDir.walkTopDown().none { it.isFile })
        } finally {
            runCatching { resolver.delete(zip, null, null) }
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsTruncatedTarAndCorruptSevenZ() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val tar = createPendingDownload(resolver, "trunc-$testId.tar", "application/x-tar")
        val seven = createPendingDownload(resolver, "bad-$testId.7z", "application/x-7z-compressed")
        resolver.openOutputStream(tar, "w")!!.use { it.write(ByteArray(40) { 0x41 }) }
        resolver.openOutputStream(seven, "w")!!.use { it.write("not-a-seven-z".toByteArray()) }
        val tarDir = java.io.File(context.cacheDir, "extract-trunc-tar-$testId").apply { mkdirs() }
        val sevenDir = java.io.File(context.cacheDir, "extract-bad-7z-$testId").apply { mkdirs() }
        try {
            val tarResult = ArchiveConverter(context).extract(
                inputUri = tar,
                outputDirectory = DocumentFile.fromFile(tarDir),
                sourceName = "trunc-$testId.tar",
            )
            val sevenResult = ArchiveConverter(context).extract(
                inputUri = seven,
                outputDirectory = DocumentFile.fromFile(sevenDir),
                sourceName = "bad-$testId.7z",
            )
            assertTrue("truncated tar should fail: $tarResult", tarResult is ConversionResult.Failure)
            assertTrue("corrupt 7z should fail: $sevenResult", sevenResult is ConversionResult.Failure)
        } finally {
            runCatching { resolver.delete(tar, null, null) }
            runCatching { resolver.delete(seven, null, null) }
            tarDir.deleteRecursively()
            sevenDir.deleteRecursively()
        }
    }

    private fun writeZip(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
        block: (ZipArchiveOutputStream) -> Unit,
    ) {
        resolver.openOutputStream(uri, "w")!!.use { raw ->
            ZipArchiveOutputStream(raw).use(block)
        }
    }

    private fun putText(zip: ZipArchiveOutputStream, name: String, content: String) {
        val bytes = content.toByteArray()
        val entry = ZipArchiveEntry(name)
        entry.size = bytes.size.toLong()
        zip.putArchiveEntry(entry)
        zip.write(bytes)
        zip.closeArchiveEntry()
    }

    private fun createTextFile(resolver: ContentResolver, name: String, content: String): Uri {
        val uri = createPendingDownload(resolver, name, "text/plain")
        resolver.openOutputStream(uri, "w")!!.use { it.write(content.toByteArray()) }
        return uri
    }

    private fun createPendingDownload(resolver: ContentResolver, name: String, mime: String): Uri = requireNotNull(
        resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                put(MediaStore.Downloads.IS_PENDING, 0)
            },
        ),
    )
}
