package com.openconvert.app.work

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主机脚本 `scripts/verify-force-stop-recovery.ps1` 的第一段：
 * 写入一份较大输入并入队 Worker，然后立刻返回，让脚本有机会 `am force-stop`。
 */
@RunWith(AndroidJUnit4::class)
class ForceStopLiveSeedTest {
    @Test
    fun enqueueLongArchiveThenReturn() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as OpenConvertApplication
        val resolver = context.contentResolver
        val stamp = System.currentTimeMillis()
        val input = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${SEED_NAME}-$stamp.bin")
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
            ),
        )
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "${SEED_NAME}-$stamp.gz")
                    put(MediaStore.Downloads.MIME_TYPE, "application/gzip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
            ),
        )
        resolver.openOutputStream(input, "w")!!.use { stream ->
            val buf = ByteArray(1024 * 1024)
            val random = java.util.Random(1)
            repeat(120) {
                random.nextBytes(buf)
                stream.write(buf)
            }
        }
        app.historyRepository.get(TASK_ID)?.let { app.historyRepository.delete(it) }
        app.historyRepository.save(
            ConversionTask(
                id = TASK_ID,
                sourceUri = input.toString(),
                sourceName = "${SEED_NAME}.bin",
                sourceFormat = FileFormat.ZIP,
                targetFormat = FileFormat.GZIP,
                outputUri = output.toString(),
                fileSize = 120L * 1024 * 1024,
                status = ConversionStatus.PENDING,
                kind = ConversionKind.ARCHIVE_COMPRESS,
                payload = ConversionPayload(
                    sourceUris = listOf(input.toString()),
                    sourceNames = listOf("${SEED_NAME}.bin"),
                ),
            ),
        )
        app.conversionScheduler.enqueue(TASK_ID)
        val active = app.conversionScheduler.activeTaskIds()
        assertTrue("work should be queued or running: $active", TASK_ID in active || active.isNotEmpty())
    }

    companion object {
        const val TASK_ID = "force-stop-live-v1"
        const val SEED_NAME = "oc-force-stop-live"
    }
}
