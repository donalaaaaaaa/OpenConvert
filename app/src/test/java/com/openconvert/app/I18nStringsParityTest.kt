package com.openconvert.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nStringsParityTest {

    @Test
    fun chineseAndEnglishStringKeysMatch() {
        val zh = stringNames(File("src/main/res/values/strings.xml"))
        val en = stringNames(File("src/main/res/values-en/strings.xml"))
        assertTrue("values/strings.xml missing", zh.isNotEmpty())
        assertTrue("values-en/strings.xml missing", en.isNotEmpty())
        assertEquals("only in zh: ${zh - en}; only in en: ${en - zh}", zh, en)
    }

    private fun stringNames(file: File): Set<String> {
        val regex = Regex("""<string\s+name="([^"]+)"""")
        return regex.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }
}
