package com.openconvert.app.upgrade

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.BuildConfig
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.converter.OfficeEngine
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficeUpgradeVerifyInstrumentedTest {

    @Test
    fun officeReplacementPreservesBasicStateAndAddsOfficeCapability() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetContext.applicationContext as OpenConvertApplication

        assertTrue("Verify phase must run against Office Release", BuildConfig.OFFICE_BUNDLED)
        assertEquals("1.2.0-office", BuildConfig.VERSION_NAME)
        assertEquals(BuildConfig.VERSION_CODE_BASE + 1, BuildConfig.VERSION_CODE)

        val history = app.database.conversionDao().getById(UpgradeStateFixture.HISTORY_ID)
        assertNotNull("Basic conversion history must survive replacement", history)
        assertEquals(UpgradeStateFixture.HISTORY_SOURCE_NAME, history!!.sourceName)
        assertEquals(UpgradeStateFixture.HISTORY_OUTPUT_NAME, history.outputName)
        assertEquals(EngineType.LIBVIPS.name, history.actualEngine)
        val fixtureUri = Uri.parse(history.sourceUri)

        val preset = app.database.presetDao().getById(UpgradeStateFixture.PRESET_ID)
        assertNotNull("Basic custom preset must survive replacement", preset)
        assertEquals(UpgradeStateFixture.PRESET_NAME, preset!!.name)
        assertEquals(2_048, preset.longestEdgePx)

        val persistedGrant = targetContext.contentResolver.persistedUriPermissions.firstOrNull {
            permission -> permission.uri == fixtureUri
        }
        assertNotNull("Persisted URI permission must survive replacement", persistedGrant)
        assertTrue(persistedGrant!!.isReadPermission)
        val displayName = targetContext.contentResolver.query(
            fixtureUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals(UpgradeStateFixture.SAF_FILE_NAME, displayName)
        val jpegHeader = targetContext.contentResolver.openInputStream(fixtureUri)!!
            .use { stream -> byteArrayOf(stream.read().toByte(), stream.read().toByte()) }
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), jpegHeader)

        assertEquals(
            listOf(FileFormat.PDF),
            FileCapabilityResolver.resolve(FileFormat.DOCX).convertTargets,
        )
        assertTrue("Upgraded package must load bundled LibreOfficeKit", OfficeEngine.isAvailable(targetContext))
    }

    @Test
    fun cleanupUpgradeFixtures() = runBlocking {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val app = targetContext.applicationContext as OpenConvertApplication
        val history = app.database.conversionDao().getById(UpgradeStateFixture.HISTORY_ID)

        if (history != null) {
            val fixtureUri = Uri.parse(history.sourceUri)
            app.database.conversionDao().delete(history)
            runCatching {
                targetContext.contentResolver.releasePersistableUriPermission(
                    fixtureUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        app.database.presetDao().deleteById(UpgradeStateFixture.PRESET_ID)

        assertNull(app.database.conversionDao().getById(UpgradeStateFixture.HISTORY_ID))
        assertNull(app.database.presetDao().getById(UpgradeStateFixture.PRESET_ID))
    }
}
