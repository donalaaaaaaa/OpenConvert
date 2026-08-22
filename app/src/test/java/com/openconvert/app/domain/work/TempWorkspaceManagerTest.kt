package com.openconvert.app.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TempWorkspaceManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun directoryIsNamespacedAndCleaned() {
        val manager = TempWorkspaceManager(tmp.newFolder("root"))
        val dir = manager.directory(TempWorkspaceManager.NS_MEDIA, "task-1")
        val file = File(dir, "chunk.bin")
        file.writeBytes(ByteArray(32))
        assertTrue(file.exists())
        assertEquals(32L, manager.totalSizeBytes())
        assertTrue(manager.cleanup(TempWorkspaceManager.NS_MEDIA, "task-1"))
        assertFalse(file.exists())
        assertEquals(0L, manager.totalSizeBytes())
    }

    @Test
    fun sanitizesPathSegments() {
        val root = tmp.newFolder("root")
        val manager = TempWorkspaceManager(root)
        val dir = manager.directory("pdf/../etc", "id with space")
        assertTrue(dir.exists())
        assertTrue(dir.absolutePath.startsWith(root.absolutePath))
        assertEquals("id_with_space", dir.name)
        assertFalse(dir.parentFile!!.name.contains(File.separator))
    }

    @Test
    fun cleanupAllRemovesTree() {
        val manager = TempWorkspaceManager(tmp.newFolder("root"))
        manager.file(TempWorkspaceManager.NS_PDF, "a.pdf").writeText("x")
        manager.directory(TempWorkspaceManager.NS_OFFICE, "t").resolve("in.docx").writeText("y")
        assertTrue(manager.totalSizeBytes() > 0L)
        assertTrue(manager.cleanupAll())
        assertEquals(0L, manager.totalSizeBytes())
    }
}
