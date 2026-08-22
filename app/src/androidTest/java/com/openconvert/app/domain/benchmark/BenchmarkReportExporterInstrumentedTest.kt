package com.openconvert.app.domain.benchmark

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenchmarkReportExporterInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var collector: BenchmarkCollector
    private val outputs = mutableListOf<Uri>()

    @Before
    fun setUp() {
        collector = BenchmarkCollector(context)
        collector.clear()
        collector.record(
            BenchmarkRecord(
                taskId = "export-test",
                inputFormat = FileFormat.PNG,
                outputFormat = FileFormat.JPG,
                inputBytes = 1_000,
                outputBytes = 400,
                elapsedMillis = 250,
                engine = EngineType.LIBVIPS,
                streamCopy = false,
                hardwareEncode = false,
                peakMemoryBytes = 12L * 1024 * 1024,
                succeeded = true,
                recordedAt = 1_000,
            ),
        )
    }

    @After
    fun tearDown() {
        collector.clear()
        outputs.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun markdownAndCsvAreWrittenThroughSaf() {
        val exporter = BenchmarkReportExporter(context)
        val markdownUri = createOutput("benchmark-${System.nanoTime()}.md", "text/markdown")
        val csvUri = createOutput("benchmark-${System.nanoTime()}.csv", "text/csv")

        val markdownResult = exporter.export(markdownUri, BenchmarkReportFormat.MARKDOWN).getOrThrow()
        val csvResult = exporter.export(csvUri, BenchmarkReportFormat.CSV).getOrThrow()

        assertEquals(1, markdownResult.recordCount)
        assertEquals(1, csvResult.recordCount)
        val markdown = read(markdownUri)
        val csv = read(csvUri)
        assertTrue(markdown.startsWith("# OpenConvert Benchmark Report"))
        assertTrue(markdown.contains("PNG → JPG"))
        assertEquals('\uFEFF', csv.first())
        assertTrue(csv.contains("export-test"))
    }

    private fun createOutput(name: String, mime: String): Uri {
        val uri = requireNotNull(
            context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/OpenConvertTest")
                },
            ),
        )
        outputs += uri
        return uri
    }

    private fun read(uri: Uri): String =
        context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
