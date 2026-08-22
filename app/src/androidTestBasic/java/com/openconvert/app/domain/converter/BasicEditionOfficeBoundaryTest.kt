package com.openconvert.app.domain.converter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.BuildConfig
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.model.FileFormat
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 阶段 09：轻量版不得误打包或误宣传 LibreOfficeKit。 */
@RunWith(AndroidJUnit4::class)
class BasicEditionOfficeBoundaryTest {

    @Test
    fun basicEditionHasNoBundledOfficeEngineOrPdfTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertFalse(BuildConfig.OFFICE_BUNDLED)
        assertTrue(
            "轻量版不应展示 Office → PDF",
            FileCapabilityResolver.resolve(FileFormat.DOCX).convertTargets.isEmpty(),
        )
        assertFalse("轻量版不应加载到 LOKit", OfficeEngine.isAvailable(context))
        assertFalse(
            "轻量版 nativeLibraryDir 不应包含 liblo-native-code.so",
            File(context.applicationInfo.nativeLibraryDir, "liblo-native-code.so").exists(),
        )
    }
}
