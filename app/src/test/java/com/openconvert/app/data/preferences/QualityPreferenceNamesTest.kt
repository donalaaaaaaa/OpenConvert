package com.openconvert.app.data.preferences

import com.openconvert.app.domain.model.QualityPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityPreferenceNamesTest {
    @Test
    fun `quality labels stay user facing chinese`() {
        assertEquals("高质量", QualityPreset.HIGH.label)
        assertEquals("平衡", QualityPreset.BALANCED.label)
        assertEquals("节省空间", QualityPreset.SMALL.label)
    }
}
