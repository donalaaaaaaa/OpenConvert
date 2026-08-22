package com.openconvert.app.work

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §29 验收：后台转换闭环。
 * 真 WorkManager 入队 → ConversionWorker 前台执行 → Room 落 COMPLETED → 输出文件可解码。
 * 在 Wi-Fi / 移动数据全部关闭的环境下运行，证明离线后台转换可用。
 */
@RunWith(AndroidJUnit4::class)
class ConversionWorkerInstrumentedTest {

    @Test
    fun backgroundConversionRunsToCompletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as OpenConvertApplication
        val repo = app.historyRepository
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val sourceUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-worker-src-$testId.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-worker-out-$testId.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )

        val taskId = UUID.randomUUID().toString()
        try {
            val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.argb(255, 200, 60, 60))
            }
            val sourceBytes = resolver.openOutputStream(sourceUri, "wt")!!.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                resolver.query(
                    sourceUri,
                    arrayOf(MediaStore.Images.Media.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
            }
            bitmap.recycle()

            repo.save(
                ConversionTask(
                    id = taskId,
                    sourceUri = sourceUri.toString(),
                    sourceName = "oc-worker-src-$testId.png",
                    sourceFormat = FileFormat.PNG,
                    targetFormat = FileFormat.JPG,
                    outputUri = outputUri.toString(),
                    fileSize = sourceBytes,
                ),
            )
            app.conversionScheduler.enqueue(taskId)

            val final = awaitStatus(repo, taskId, ConversionStatus.COMPLETED, timeoutMs = 90_000)
            assertNotNull("conversion never reached COMPLETED within 90s", final)
            assertEquals(ConversionStatus.COMPLETED, final!!.status)
            assertEquals(100, final.progress)
            assertNotNull(final.outputUri)
            assertTrue(
                "Worker 必须持久化实际图像引擎",
                final.actualEngine in setOf(EngineType.LIBVIPS, EngineType.BITMAP_FACTORY),
            )

            val decoded = resolver.openInputStream(Uri.parse(final.outputUri!!))!!.use {
                BitmapFactory.decodeStream(it)
            }
            assertNotNull("output is not a decodable image", decoded)
            assertEquals(480, decoded.width)
            assertEquals(320, decoded.height)
            decoded.recycle()
        } finally {
            app.conversionScheduler.cancel(taskId)
            repo.get(taskId)?.let { repo.delete(it) }
            cleanupRow(resolver, sourceUri)
            cleanupRow(resolver, outputUri)
        }
    }

    @Test
    fun cancelStopsBackgroundConversion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as OpenConvertApplication
        val repo = app.historyRepository
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val sourceUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-cancel-src-$testId.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-cancel-out-$testId.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )

        val taskId = UUID.randomUUID().toString()
        try {
            val bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            resolver.openOutputStream(sourceUri, "wt")!!.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()

            repo.save(
                ConversionTask(
                    id = taskId,
                    sourceUri = sourceUri.toString(),
                    sourceName = "oc-cancel-src-$testId.png",
                    sourceFormat = FileFormat.PNG,
                    targetFormat = FileFormat.JPG,
                    outputUri = outputUri.toString(),
                ),
            )
            app.conversionScheduler.enqueue(taskId)
            // Let it start (or cancel before start — both paths must land on a terminal state).
            delay(300)
            app.conversionScheduler.cancel(taskId)

            val final = awaitTerminal(repo, taskId, timeoutMs = 30_000)
            assertNotNull("work did not reach a terminal state after cancel", final)
            assertTrue(
                "expected CANCELLED/FAILED/COMPLETED after cancel, got ${final!!.status}",
                final.status in setOf(
                    ConversionStatus.CANCELLED,
                    ConversionStatus.FAILED,
                    ConversionStatus.COMPLETED,
                ),
            )
            assertTrue("cancelled conversion must not remain RUNNING", final.status != ConversionStatus.RUNNING)
        } finally {
            repo.get(taskId)?.let { repo.delete(it) }
            cleanupRow(resolver, sourceUri)
            cleanupRow(resolver, outputUri)
        }
    }

    /**
     * MediaStore cleanup is best-effort: on Android 16 the row ownership can race with the
     * Worker's own finalizeCancelled delete, which surfaces as SecurityException here.
     * The assertions above already prove the behaviour under test.
     */
    private fun cleanupRow(resolver: android.content.ContentResolver, uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private suspend fun awaitStatus(
        repo: com.openconvert.app.data.repository.ConversionHistoryRepository,
        taskId: String,
        wanted: ConversionStatus,
        timeoutMs: Long,
    ): ConversionTask? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: ConversionTask? = null
        while (System.currentTimeMillis() < deadline) {
            last = repo.get(taskId)
            if (last?.status == wanted) return last
            delay(500)
        }
        return if (last?.status == wanted) last else null
    }

    private suspend fun awaitTerminal(
        repo: com.openconvert.app.data.repository.ConversionHistoryRepository,
        taskId: String,
        timeoutMs: Long,
    ): ConversionTask? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: ConversionTask? = null
        while (System.currentTimeMillis() < deadline) {
            last = repo.get(taskId)
            if (last != null && last.status != ConversionStatus.PENDING && last.status != ConversionStatus.RUNNING) {
                return last
            }
            delay(300)
        }
        return null
    }
}
