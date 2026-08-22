package com.openconvert.app.domain.benchmark

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.openconvert.app.domain.converter.ConversionExecutor
import com.openconvert.app.domain.converter.ExecutionResult
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §11.1 指标采集的**生产路径**验收：跑一次真实转换，
 * 确认 ConversionExecutor 写出了带真实耗时/引擎/压缩率的记录。
 *
 * 与 BenchmarkCollectorInstrumentedTest 的区别：那个测的是落盘组件本身，
 * 这个测的是"转换真的会触发采集"——接线漏了的话那个测试仍会全绿。
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkProductionPathTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var collector: BenchmarkCollector
    private val mediaUris = mutableListOf<android.net.Uri>()

    @Before
    fun setUp() {
        collector = BenchmarkCollector(context)
        collector.clear()
    }

    @After
    fun tearDown() {
        collector.clear()
        mediaUris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        mediaUris.clear()
    }

    /**
     * 造一张真 PNG 并通过 MediaStore 暴露为 content:// —— 工程里没有配置
     * FileProvider，直接用 MediaStore 是唯一不改生产 manifest 的可用路径。
     *
     * 内容用**伪随机噪声**而非纯色图形：平坦图像的 PNG 无损压缩效率远高于
     * JPEG，转 JPG 会变大（实测 -56%）；噪声图才能稳定验证"压缩率为正"这条路径。
     */
    private fun createSourcePng(width: Int, height: Int): android.net.Uri {
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val random = java.util.Random(42) // 固定种子，输出体积可复现
        val pixels = IntArray(width * height) {
            0xFF000000.toInt() or (random.nextInt() and 0x00FFFFFF)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val uri = insertIntoDownloads("bench-src-${System.nanoTime()}.png", "image/png")
        context.contentResolver.openOutputStream(uri)!!.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        mediaUris += uri
        return uri
    }

    private fun insertIntoDownloads(name: String, mime: String): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
        }
        return context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        )!!
    }

    private fun createOutputUri(): android.net.Uri =
        insertIntoDownloads("bench-out-${System.nanoTime()}.jpg", "image/jpeg")
            .also { mediaUris += it }

    @Test
    fun realConversionWritesABenchmarkRecord() = runBlocking {
        val source = createSourcePng(1200, 900)
        val output = createOutputUri()
        val sourceSize = context.contentResolver.openInputStream(source)!!.use {
            it.readBytes().size.toLong()
        }

        val task = ConversionTask(
            id = "bench-prod-1",
            sourceUri = source.toString(),
            sourceName = "bench-src.png",
            sourceFormat = FileFormat.PNG,
            targetFormat = FileFormat.JPG,
            outputUri = output.toString(),
            fileSize = sourceSize,
            quality = QualityPreset.BALANCED,
            status = ConversionStatus.RUNNING,
            kind = ConversionKind.SINGLE,
            payload = ConversionPayload(),
            outputName = "bench-out.jpg",
        )

        try {
            val result = ConversionExecutor(context).execute(task) {}
            assertTrue("转换本身应成功，实际 $result", result is ExecutionResult.Success)
            assertTrue(
                "执行结果必须暴露实际图像引擎",
                (result as ExecutionResult.Success).actualEngine != null,
            )

            val records = collector.readAll()
            assertEquals("生产路径应恰好写一条记录", 1, records.size)

            val json = records.single()
            assertEquals("bench-prod-1", json.getString("taskId"))
            assertEquals("PNG → JPG", json.getString("route"))
            assertTrue("耗时应为正数", json.getLong("elapsedMillis") > 0)
            assertEquals(sourceSize, json.getLong("inputBytes"))
            assertTrue("输出体积应被记录", json.getLong("outputBytes") > 0)
            assertTrue(json.getBoolean("succeeded"))
            assertTrue("峰值内存应被采样", json.getLong("peakMemoryBytes") > 0)
            // PNG→JPG 应当明显变小，压缩率为正。
            assertTrue(
                "PNG→JPG 压缩率应为正，实际 ${json.getInt("reductionPercent")}",
                json.getInt("reductionPercent") > 0,
            )
            assertNotNull(json.getString("engine"))
        } finally {
            runCatching { context.contentResolver.delete(output, null, null) }
        }
    }

    @Test
    fun failedConversionIsAlsoRecorded() = runBlocking {
        // 不存在的源 → 转换必败；指标仍应落一条 succeeded=false。
        val task = ConversionTask(
            id = "bench-prod-fail",
            sourceUri = "content://media/external/downloads/999999999",
            sourceName = "missing.png",
            sourceFormat = FileFormat.PNG,
            targetFormat = FileFormat.JPG,
            outputUri = createOutputUri().toString(),
            fileSize = 1024,
            status = ConversionStatus.RUNNING,
            kind = ConversionKind.SINGLE,
            payload = ConversionPayload(),
        )

        val result = ConversionExecutor(context).execute(task) {}
        assertTrue("应当失败", result is ExecutionResult.Failure)

        val records = collector.readAll()
        assertEquals(1, records.size)
        assertEquals(false, records.single().getBoolean("succeeded"))
    }

    @Test
    fun compatibleVideoUsesThePlannerStreamCopyPath() = runBlocking {
        val generated = java.io.File(context.cacheDir, "bench-remux-${System.nanoTime()}.mp4")
        val generation = FFmpegKit.executeWithArguments(
            arrayOf(
                "-y",
                "-f", "lavfi",
                "-i", "color=c=blue:s=160x120:d=1",
                "-f", "lavfi",
                "-i", "sine=frequency=440:duration=1",
                "-c:v", "mpeg4",
                "-c:a", "aac",
                "-shortest",
                generated.absolutePath,
            ),
        )
        assertTrue(
            "无法生成 remux 测试视频：${generation.output}",
            ReturnCode.isSuccess(generation.returnCode),
        )

        val source = insertIntoDownloads(
            "bench-remux-${System.nanoTime()}.mov",
            "video/quicktime",
        )
        val output = insertIntoDownloads(
            "bench-remux-${System.nanoTime()}.mp4",
            "video/mp4",
        )
        mediaUris += source
        mediaUris += output
        try {
            context.contentResolver.openOutputStream(source, "wt")!!.use { target ->
                generated.inputStream().use { input -> input.copyTo(target) }
            }
            val sourceSize = generated.length()
            val task = ConversionTask(
                id = "bench-remux",
                sourceUri = source.toString(),
                sourceName = "bench-remux.mov",
                sourceFormat = FileFormat.MOV,
                targetFormat = FileFormat.MP4,
                outputUri = output.toString(),
                fileSize = sourceSize,
                quality = QualityPreset.BALANCED,
                status = ConversionStatus.RUNNING,
                kind = ConversionKind.SINGLE,
                payload = ConversionPayload(),
                outputName = "bench-remux.mp4",
            )

            val result = ConversionExecutor(context).execute(task) {}
            assertTrue("兼容编码应成功换容器，实际 $result", result is ExecutionResult.Success)
            assertEquals(
                "流拷贝实际由 FFmpegKit 完成",
                com.openconvert.app.domain.engine.EngineType.FFMPEG_KIT,
                (result as ExecutionResult.Success).actualEngine,
            )

            val json = collector.readAll().single()
            assertTrue("Planner 应识别为流拷贝", json.getBoolean("streamCopy"))
            assertEquals("FFMPEG_KIT", json.getString("engine"))
            assertEquals(false, json.getBoolean("hardwareEncode"))
        } finally {
            generated.delete()
        }
    }
}
