package com.openconvert.app.domain.converter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.BuildConfig
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 正式路径只认 Flavor 内置库。往 filesDir 塞一份假 Office Pack 不得改变可用性。
 */
@RunWith(AndroidJUnit4::class)
class OfficePackIsolationInstrumentedTest {

    @Test
    fun filesDirPackDoesNotChangeEngineAvailability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packLib = File(context.filesDir, "office-pack/lib/arm64-v8a")
        packLib.mkdirs()
        val fake = File(packLib, "liblo-native-code.so")
        try {
            fake.writeBytes(ByteArray(12_000_000))
            assertTrue(fake.isFile && fake.length() > 10_000_000L)

            val available = OfficeEngine.isAvailable(context)
            if (BuildConfig.OFFICE_BUNDLED) {
                assertTrue("Office Flavor 必须靠 APK 内置库可用", available)
            } else {
                assertFalse("Basic Flavor 即使有 filesDir pack 也不可用", available)
            }
        } finally {
            File(context.filesDir, "office-pack").deleteRecursively()
        }
    }
}
