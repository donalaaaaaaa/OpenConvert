package com.openconvert.app.domain.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageEditMathTest {
    @Test
    fun parsesAspectRatios() {
        assertEquals(1f to 1f, ImageEditMath.parseAspectRatio("1:1"))
        assertEquals(16f to 9f, ImageEditMath.parseAspectRatio("16:9"))
        assertEquals(9f to 16f, ImageEditMath.parseAspectRatio("9:16"))
        assertEquals(4f to 3f, ImageEditMath.parseAspectRatio("4:3"))
    }

    @Test
    fun freeOrBlankReturnsNull() {
        assertNull(ImageEditMath.parseAspectRatio("free"))
        assertNull(ImageEditMath.parseAspectRatio(""))
        assertNull(ImageEditMath.parseAspectRatio("  "))
    }

    @Test
    fun invalidAspectReturnsNull() {
        assertNull(ImageEditMath.parseAspectRatio("abc"))
        assertNull(ImageEditMath.parseAspectRatio("1:0"))
        assertNull(ImageEditMath.parseAspectRatio("0:1"))
        assertNull(ImageEditMath.parseAspectRatio("1"))
    }

    @Test
    fun coverCropKeepsTargetRatio() {
        // 4000x3000 (4:3) → 1:1 方形：按高度裁 → 3000x3000
        val square = ImageEditMath.coverCropSize(4000, 3000, 1f to 1f)
        assertEquals(3000 to 3000, square)

        // 4000x3000 → 16:9：按高度裁 → 3000*16/9 ≈ 5333 → 裁剪不超过宽度 → 4000x2250
        val wide = ImageEditMath.coverCropSize(4000, 3000, 16f to 9f)
        assertEquals(4000 to 2250, wide)

        // 3000x4000 (竖图) → 9:16：源图更宽(0.75 > 0.5625) → 按高度裁 → 4000*9/16=2250 → 2250x4000
        val portrait = ImageEditMath.coverCropSize(3000, 4000, 9f to 16f)
        assertEquals(2250 to 4000, portrait)
    }

    @Test
    fun coverCropNeverExceedsSource() {
        val result = ImageEditMath.coverCropSize(100, 50, 1f to 1f)
        assertEquals(50 to 50, result)
    }

    @Test
    fun rotateCodeMapsDegrees() {
        assertEquals(0, ImageEditMath.rotateCode(0))
        assertEquals(1, ImageEditMath.rotateCode(90))
        assertEquals(2, ImageEditMath.rotateCode(180))
        assertEquals(3, ImageEditMath.rotateCode(270))
        assertEquals(1, ImageEditMath.rotateCode(450)) // 90*5
        assertEquals(0, ImageEditMath.rotateCode(360))
    }
}
