package com.openconvert.app.domain.work

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFileStabilityInstrumentedTest {
    @Test
    fun streams100Mb() = streamExact(100L * 1024 * 1024, "100MB")

    @Test
    fun streams500Mb() = streamExact(500L * 1024 * 1024, "500MB")

    @Test
    fun streams1Gb() = streamExact(1024L * 1024 * 1024, "1GB")

    @Test
    fun streams2Gb() = streamExact(2L * 1024 * 1024 * 1024, "2GB")

    @Test
    fun streams4Gb() = streamExact(4L * 1024 * 1024 * 1024, "4GB")

    private fun streamExact(size: Long, label: String) = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val usable = context.cacheDir.usableSpace
        val required = size + StorageGuard.SAFETY_MARGIN_BYTES
        assumeTrue(
            "usable=$usable required=$required",
            StorageGuard.hasEnoughSpace(usable, required),
        )

        val target = File(context.cacheDir, "openconvert-stability-$label.bin")
        val started = System.nanoTime()
        try {
            val copied = FiniteByteStream(size).use { input ->
                FileOutputStream(target).use { output -> BoundedIo.copy(input, output) }
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertEquals(size, copied)
            assertEquals(size, target.length())
            File(context.cacheDir, "openconvert-stability-report-$label.txt")
                .writeText("$label PASS ${elapsedMs}ms copied=$copied usable=$usable")
        } finally {
            target.delete()
        }
    }

    private class FiniteByteStream(size: Long) : InputStream() {
        private var remaining = size

        override fun read(): Int {
            if (remaining <= 0L) return -1
            remaining--
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) return -1
            val n = minOf(length.toLong(), remaining).toInt()
            remaining -= n
            return n
        }
    }
}
