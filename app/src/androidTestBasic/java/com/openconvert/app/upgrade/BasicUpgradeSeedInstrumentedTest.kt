package com.openconvert.app.upgrade

import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.BuildConfig
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.data.local.ConversionEntity
import com.openconvert.app.data.local.PresetEntity
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicUpgradeSeedInstrumentedTest {

    @Test
    fun seedRoomAndPersistedUriState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val app = targetContext.applicationContext as OpenConvertApplication

        assertFalse("Seed phase must run against Basic Release", BuildConfig.OFFICE_BUNDLED)
        assertEquals("1.2.0", BuildConfig.VERSION_NAME)
        assertEquals(BuildConfig.VERSION_CODE_BASE, BuildConfig.VERSION_CODE)

        val persistedGrant = targetContext.contentResolver.persistedUriPermissions.firstOrNull {
            permission ->
            permission.isReadPermission && targetContext.contentResolver.query(
                permission.uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == UpgradeStateFixture.SAF_FILE_NAME
            } == true
        }
        assertNotNull(
            "Select the bundled upgrade marker through Basic's system file picker first",
            persistedGrant,
        )
        val fixtureUri = persistedGrant!!.uri

        app.database.conversionDao().upsert(
            ConversionEntity(
                id = UpgradeStateFixture.HISTORY_ID,
                sourceUri = fixtureUri.toString(),
                sourceName = UpgradeStateFixture.HISTORY_SOURCE_NAME,
                sourceFormat = FileFormat.PNG.name,
                targetFormat = FileFormat.JPG.name,
                outputUri = null,
                fileSize = 12_345,
                outputSize = 6_789,
                quality = QualityPreset.HIGH.name,
                resolution = ResolutionPreset.ORIGINAL.name,
                progress = 100,
                status = ConversionStatus.COMPLETED.name,
                createdAt = 1_755_744_000_000,
                completedAt = 1_755_744_001_234,
                kind = ConversionKind.SINGLE.name,
                payloadJson = null,
                errorMessage = null,
                outputName = UpgradeStateFixture.HISTORY_OUTPUT_NAME,
                actualEngine = EngineType.LIBVIPS.name,
            ),
        )
        app.database.presetDao().insertOrUpdate(
            PresetEntity(
                id = UpgradeStateFixture.PRESET_ID,
                category = FileCategory.IMAGE.name,
                name = UpgradeStateFixture.PRESET_NAME,
                description = "Basic Release seeded upgrade marker",
                targetFormat = FileFormat.JPG.name,
                quality = QualityPreset.HIGH.name,
                resolution = ResolutionPreset.ORIGINAL.name,
                stripMetadata = true,
                isDefault = false,
                isBuiltIn = false,
                createdAt = 1_755_744_000_000,
                longestEdgePx = 2_048,
            ),
        )

        assertNotNull(app.database.conversionDao().getById(UpgradeStateFixture.HISTORY_ID))
        assertNotNull(app.database.presetDao().getById(UpgradeStateFixture.PRESET_ID))
        assertTrue(persistedGrant.isReadPermission)
        val jpegHeader = targetContext.contentResolver.openInputStream(fixtureUri)!!
            .use { stream -> byteArrayOf(stream.read().toByte(), stream.read().toByte()) }
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), jpegHeader)
    }
}
