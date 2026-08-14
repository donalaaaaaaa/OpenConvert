package com.openconvert.app.domain.converter

/**
 * Thin JNI binding over libvips_android.so.
 * Loaded once; on failure callers must fall back to BitmapFactory paths.
 */
object VipsNative {
    @Volatile
    private var loadState: LoadState = LoadState.UNTRIED

    private enum class LoadState { UNTRIED, OK, FAILED }

    val isAvailable: Boolean
        get() {
            if (loadState == LoadState.UNTRIED) {
                synchronized(this) {
                    if (loadState == LoadState.UNTRIED) {
                        loadState = try {
                            System.loadLibrary("vips_android")
                            LoadState.OK
                        } catch (t: Throwable) {
                            android.util.Log.w("OpenConvert", "libvips unavailable: $t")
                            LoadState.FAILED
                        }
                    }
                }
            }
            return loadState == LoadState.OK
        }

    /** Returns [width, height, bands, bandFormat] or throws on decode failure. */
    external fun probeBuffer(input: ByteArray): IntArray

    /**
     * Resize/convert an image buffer.
     * mode: 0 = stretch, 1 = cover-crop, 2 = contain.
     * fmt: "jpg" | "png" | "webp"; quality 1..100.
     * rotate: 0 = none, 1 = 90° CW, 2 = 180°, 3 = 270° CW.
     * flip: 0 = none, 1 = horizontal, 2 = vertical.
     */
    external fun convertBuffer(
        input: ByteArray,
        targetWidth: Int,
        targetHeight: Int,
        mode: Int,
        fmt: String,
        quality: Int,
        rotate: Int = 0,
        flip: Int = 0,
    ): ByteArray
}
