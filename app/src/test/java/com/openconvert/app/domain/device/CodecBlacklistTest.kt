package com.openconvert.app.domain.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecBlacklistTest {

    @Test
    fun recordedDeviceCodecIsSkipped() {
        val list = CodecBlacklist()
        list.record("OnePlus", "PHY110", "c2.qti.avc.encoder")
        assertTrue(list.shouldSkipHardware("OnePlus", "PHY110", "c2.qti.avc.encoder"))
        assertFalse(list.shouldSkipHardware("OnePlus", "PHY110", "c2.qti.vp8.encoder"))
        assertFalse(list.shouldSkipHardware("Google", "Pixel 8", "c2.qti.avc.encoder"))
    }

    @Test
    fun deviceMatchIsCaseInsensitive() {
        val list = CodecBlacklist()
        list.record("oneplus", "phy110", "C2.QTI.AVC.ENCODER")
        assertTrue(list.shouldSkipHardware("OnePlus", "PHY110", "c2.qti.avc.encoder"))
    }

    @Test
    fun anyCodecQueryMatchesWholeDevice() {
        val list = CodecBlacklist()
        list.record("OnePlus", "PHY110", "c2.qti.avc.encoder")
        assertTrue(list.shouldSkipHardware("OnePlus", "PHY110"))
        assertFalse(list.shouldSkipHardware("Samsung", "SM-S9110"))
    }
}
