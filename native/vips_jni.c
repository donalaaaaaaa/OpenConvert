/*
 * JNI bridge: a tiny, focused C API over libvips for OpenConvert.
 * Only the ops the app needs:
 *   - vips_image_new_from_buffer (probe width/height/bands/format)
 *   - thumbnail_buffer (scale with region cropping / centre)
 *   - write to buffer as JPEG/PNG/WEBP
 *
 * All static deps (glib, jpeg, png, webp, zlib, iconv, ffi, pcre2, vips)
 * are linked into one libvips_android.so; a version script hides every
 * symbol except the JNI entry points.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <vips/vips.h>

static void throw_vips(JNIEnv *env) {
    const char *msg = vips_error_buffer();
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (cls) (*env)->ThrowNew(env, cls, msg ? msg : "vips error");
    vips_error_clear();
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    if (vips_init("openconvert") != 0) return JNI_ERR;
    vips_concurrency_set(4);
    return JNI_VERSION_1_6;
}

/* Probe an image header: returns int[4] = {width, height, bands, format} */
JNIEXPORT jintArray JNICALL
Java_com_openconvert_app_domain_converter_VipsNative_probeBuffer(
    JNIEnv *env, jclass cls, jbyteArray input) {
    (void)cls;
    jsize len = (*env)->GetArrayLength(env, input);
    jbyte *buf = (*env)->GetByteArrayElements(env, input, NULL);
    if (!buf) return NULL;

    VipsImage *img = vips_image_new_from_buffer((void *)buf, (size_t)len, "", NULL);
    (*env)->ReleaseByteArrayElements(env, input, buf, JNI_ABORT);
    if (!img) { throw_vips(env); return NULL; }

    /* Apply EXIF orientation so probed dims match visible pixels. */
    {
        VipsImage *rotated = NULL;
        if (vips_autorot(img, &rotated, NULL) == 0) {
            g_object_unref(img);
            img = rotated;
        } else {
            vips_error_clear();
        }
    }

    int result[4];
    result[0] = vips_image_get_width(img);
    result[1] = vips_image_get_height(img);
    result[2] = vips_image_get_bands(img);
    result[3] = (int)vips_image_get_format(img);
    g_object_unref(img);

    jintArray out = (*env)->NewIntArray(env, 4);
    (*env)->SetIntArrayRegion(env, out, 0, 4, result);
    return out;
}

/*
 * Scale an image buffer to target width/height.
 * mode: 0 = scale (ignore aspect), 1 = cover-crop (fill), 2 = contain (fit).
 * rotate: 0 = none, 1 = 90 CW, 2 = 180, 3 = 270 CW.
 * flip: 0 = none, 1 = horizontal, 2 = vertical.
 * Returns encoded bytes in fmt: "jpg" | "png" | "webp".
 * quality 1..100.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_openconvert_app_domain_converter_VipsNative_convertBuffer(
    JNIEnv *env, jclass cls, jbyteArray input, jint tw, jint th,
    jint mode, jstring fmt, jint quality, jint rotate, jint flip) {
    (void)cls;
    jsize len = (*env)->GetArrayLength(env, input);
    jbyte *buf = (*env)->GetByteArrayElements(env, input, NULL);
    if (!buf) return NULL;

    VipsImage *img = vips_image_new_from_buffer((void *)buf, (size_t)len, "", NULL);
    (*env)->ReleaseByteArrayElements(env, input, buf, JNI_ABORT);
    if (!img) { throw_vips(env); return NULL; }

    /* Apply EXIF orientation up-front so resize math matches visible pixels. */
    {
        VipsImage *rotated = NULL;
        if (vips_autorot(img, &rotated, NULL) == 0) {
            g_object_unref(img);
            img = rotated;
        } else {
            vips_error_clear(); /* orientation missing -> keep as is */
        }
    }

    /* User rotation: 90 / 180 / 270 degrees clockwise. */
    if (rotate == 1 || rotate == 2 || rotate == 3) {
        VipsImage *rotated = NULL;
        VipsAngle angle = (rotate == 1) ? VIPS_ANGLE_D90 :
                         (rotate == 2) ? VIPS_ANGLE_D180 : VIPS_ANGLE_D270;
        if (vips_rot(img, &rotated, angle, NULL) == 0) {
            g_object_unref(img);
            img = rotated;
        } else {
            vips_error_clear(); /* keep original if rotation fails */
        }
    }

    /* Flip: 1 = horizontal (mirror left-right), 2 = vertical (mirror top-bottom). */
    if (flip == 1 || flip == 2) {
        VipsImage *flipped = NULL;
        VipsDirection dir = (flip == 1) ? VIPS_DIRECTION_HORIZONTAL : VIPS_DIRECTION_VERTICAL;
        if (vips_flip(img, &flipped, dir, NULL) == 0) {
            g_object_unref(img);
            img = flipped;
        } else {
            vips_error_clear();
        }
    }

    double iw = vips_image_get_width(img);
    double ih = vips_image_get_height(img);
    double hscale = (double)tw / iw;
    double vscale = (double)th / ih;

    VipsImage *scaled = NULL;
    int failed = 0;

    if (mode == 0) {
        failed = vips_resize(img, &scaled, hscale, "vscale", vscale, NULL);
    } else if (mode == 1) {
        /* cover: scale by the LARGER ratio then crop centre */
        double s = hscale > vscale ? hscale : vscale;
        VipsImage *tmp = NULL;
        if (vips_resize(img, &tmp, s, NULL) == 0) {
            int cw = tw, ch = th;
            if (vips_image_get_width(tmp) < cw) cw = vips_image_get_width(tmp);
            if (vips_image_get_height(tmp) < ch) ch = vips_image_get_height(tmp);
            int left = (vips_image_get_width(tmp) - cw) / 2;
            int top = (vips_image_get_height(tmp) - ch) / 2;
            failed = vips_crop(tmp, &scaled, left, top, cw, ch, NULL);
            g_object_unref(tmp);
        } else {
            failed = 1;
        }
    } else {
        /* contain: scale by SMALLER ratio (fits inside) */
        double s = hscale < vscale ? hscale : vscale;
        failed = vips_resize(img, &scaled, s, NULL);
    }
    g_object_unref(img);
    if (failed || !scaled) { throw_vips(env); return NULL; }

    const char *fm = (*env)->GetStringUTFChars(env, fmt, NULL);
    void *out = NULL;
    size_t outlen = 0;
    int enc_failed = 1;

    if (strcmp(fm, "jpg") == 0 || strcmp(fm, "jpeg") == 0) {
        enc_failed = vips_jpegsave_buffer(scaled, &out, &outlen, "Q", quality, NULL);
    } else if (strcmp(fm, "png") == 0) {
        enc_failed = vips_pngsave_buffer(scaled, &out, &outlen, NULL);
    } else if (strcmp(fm, "webp") == 0) {
        enc_failed = vips_webpsave_buffer(scaled, &out, &outlen, "Q", quality, NULL);
    } else {
        enc_failed = 1;
        vips_error("openconvert", "unsupported output format");
    }
    (*env)->ReleaseStringUTFChars(env, fmt, fm);
    g_object_unref(scaled);

    if (enc_failed || !out) { throw_vips(env); return NULL; }

    jbyteArray result = (*env)->NewByteArray(env, (jsize)outlen);
    (*env)->SetByteArrayRegion(env, result, 0, (jsize)outlen, (jbyte *)out);
    g_free(out);
    return result;
}
