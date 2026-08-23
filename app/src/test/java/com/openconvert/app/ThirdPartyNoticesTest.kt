package com.openconvert.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThirdPartyNoticesTest {

    @Test
    fun noticesStayInSyncAndListRequiredComponents() {
        val root = File("../THIRD_PARTY_NOTICES.md")
        val asset = File("src/main/assets/THIRD_PARTY_NOTICES.md")
        assertTrue("repo-root notices missing", root.isFile)
        assertTrue("asset notices missing", asset.isFile)
        val text = root.readText()
        assertEquals(text, asset.readText())
        for (token in listOf(
            "libvips",
            "LibreOfficeKit",
            "FFmpegKit",
            "PdfBox",
            "Commons Compress",
            "LiTr",
            "AndroidX",
            "LGPL-2.1",
            "LGPL-3.0",
            "MPL-2.0",
            "Apache-2.0",
            "BSD-2-Clause",
            "META-INF/NOTICE",
        )) {
            assertTrue("$token missing from THIRD_PARTY_NOTICES.md", text.contains(token))
        }
    }
}
