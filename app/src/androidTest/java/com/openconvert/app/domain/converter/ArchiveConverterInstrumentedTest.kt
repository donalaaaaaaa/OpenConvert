package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.FileFormat
import java.io.ByteArrayOutputStream
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
