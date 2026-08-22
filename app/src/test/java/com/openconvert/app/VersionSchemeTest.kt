package com.openconvert.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 13: Basic is the published versionCode; Office is always base + 1
 * so a graphic installer can replace Basic without adb -r.
 */
class VersionSchemeTest {

    @Test
    fun editionVersionCodesStayOrdered() {
        assertTrue(
            "VERSION_CODE_BASE must outrank v1.0's 100",
            BuildConfig.VERSION_CODE_BASE > 100,
        )
        assertTrue(
            BuildConfig.VERSION_NAME.matches(Regex("""^\d+\.\d+\.\d+.*""")),
        )
        if (BuildConfig.OFFICE_BUNDLED) {
            assertEquals(BuildConfig.VERSION_CODE_BASE + 1, BuildConfig.VERSION_CODE)
            assertTrue(BuildConfig.VERSION_NAME.contains("-office"))
        } else {
            assertEquals(BuildConfig.VERSION_CODE_BASE, BuildConfig.VERSION_CODE)
            assertFalse(BuildConfig.VERSION_NAME.contains("-office"))
        }
    }
}
