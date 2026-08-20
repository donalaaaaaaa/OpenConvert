package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.testassets.TestAssetFactory
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 阶段一稳定性验收（计划书 §4.1 / §4.2）：异常输入不得让转换崩溃，
 * 必须落到 Failure 并带可读原因。
 *
 * 覆盖 §4.1 的：输入文件损坏、空文件、扩展名错误、输出目标失效。
 * 验收标准对应「不因单个坏文件导致整个批量任务崩溃」与
 * 「错误能够向用户显示具体原因」。
 */
@RunWith(AndroidJUnit4::class)
class CorruptInputInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver: ContentResolver get() = context.contentResolver

    @Test
    fun emptyImageFileFailsGracefully() = runBlocking {
        val bytes = ByteArray(0)
        val result = convertImage(bytes, "empty-$")
        assertFailureWithReason(result)
    }

    @Test
    fun truncatedPngFailsGracefully() = runBlocking {
        // 只有 8 字节 PNG 签名，没有 IHDR — 解码器必然失败。
        val result = convertImage(TestAssetFactory.PNG_MAGIC, "truncated-$")
        assertFailureWithReason(result)
    }

    @Test
    fun corruptedPngBodyFailsGracefully() = runBlocking {
        // 头合法、主体是确定性垃圾字节。
        val bytes = TestAssetFactory.PNG_MAGIC + TestAssetFactory.deterministicBytes(8192, seed = 3L)
        val result = convertImage(bytes, "corrupt-$")
        assertFailureWithReason(result)
    }

    @Test
    fun contentNotMatchingExtensionFailsGracefully() = runBlocking {
        // 存成 .png，内容其实是 PDF：声明 PNG→JPG 的任务必须失败而非崩溃。
        val bytes = TestAssetFactory.PDF_MAGIC + TestAssetFactory.deterministicBytes(1024, seed = 5L)
        val result = convertImage(bytes, "liar-$")
        assertFailureWithReason(result)
    }

    @Test
    fun deletedOutputTargetFailsGracefully() = runBlocking {
        val testId = UUID.randomUUID().toString()
        val source = insertPending("valid-$testId.png", "image/png")
        val output = insertPending("gone-$testId.jpg", "image/jpeg")

        try {
            // 写入一张真实可解码的 PNG。
            val bitmap = android.graphics.Bitmap.createBitmap(
                64, 64, android.graphics.Bitmap.Config.ARGB_8888,
            ).apply { eraseColor(android.graphics.Color.GREEN) }
            resolver.openOutputStream(source, "wt")!!.use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            publish(source)

            // 转换前删掉输出行 → SAF URI 失效（§4.1「输出目录失效」）。
            resolver.delete(output, null, null)

            val result = ImageConverter(resolver).convert(
                task(source, output, FileFormat.PNG, FileFormat.JPG),
            )
            assertFailureWithReason(result)
        } finally {
            runCatching { resolver.delete(source, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    /** 坏文件之后紧接一个好文件仍能成功 —— 证明失败不污染后续任务。 */
    @Test
    fun goodConversionStillWorksAfterACorruptOne() = runBlocking {
        val corrupt = convertImage(
            TestAssetFactory.PNG_MAGIC + TestAssetFactory.deterministicBytes(4096, seed = 11L),
            "poison-$",
        )
        assertFailureWithReason(corrupt)

        val testId = UUID.randomUUID().toString()
        val source = insertPending("recover-$testId.png", "image/png")
        val output = insertPending("recover-out-$testId.jpg", "image/jpeg")
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(
                120, 90, android.graphics.Bitmap.Config.ARGB_8888,
            ).apply { eraseColor(android.graphics.Color.MAGENTA) }
            resolver.openOutputStream(source, "wt")!!.use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            publish(source)

            val result = ImageConverter(resolver).convert(
                task(source, output, FileFormat.PNG, FileFormat.JPG),
            )
            assertTrue("坏文件之后的正常转换应成功，got $result", result is ConversionResult.Success)
        } finally {
            runCatching { resolver.delete(source, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    // ---- helpers ----

    private suspend fun convertImage(sourceBytes: ByteArray, namePrefix: String): ConversionResult {
        val testId = UUID.randomUUID().toString()
        val source = insertPending("$namePrefix$testId.png", "image/png")
        val output = insertPending("$namePrefix$testId-out.jpg", "image/jpeg")
        return try {
            resolver.openOutputStream(source, "wt")!!.use { it.write(sourceBytes) }
            publish(source)
            ImageConverter(resolver).convert(task(source, output, FileFormat.PNG, FileFormat.JPG))
        } finally {
            runCatching { resolver.delete(source, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun task(
        source: Uri,
        output: Uri,
        from: FileFormat,
        to: FileFormat,
    ) = ConversionTask(
        id = UUID.randomUUID().toString(),
        sourceUri = source.toString(),
        sourceName = "test.${from.preferredExtension}",
        sourceFormat = from,
        targetFormat = to,
        outputUri = output.toString(),
        fileSize = 1L,
    )

    /**
     * 异常输入必须落到 Failure（而非抛异常或假成功），且消息非空可读。
     * 计划书 §4 验收：「错误能够向用户显示具体原因」。
     */
    private fun assertFailureWithReason(result: ConversionResult) {
        assertTrue("异常输入应返回 Failure，got $result", result is ConversionResult.Failure)
        val message = (result as ConversionResult.Failure).message
        assertTrue("失败必须带原因", message.isNotBlank())
        assertFalse(
            "失败原因不能是无信息量的占位文案：$message",
            message.equals("Conversion failed", ignoreCase = true),
        )
    }

    private fun insertPending(name: String, mime: String): Uri = requireNotNull(
        resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        ),
    )

    private fun publish(uri: Uri) {
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }
}
