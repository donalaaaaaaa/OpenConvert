# OpenConvert R8 / ProGuard rules.
# Enables minifyEnabled + shrinkResources for release while keeping every
# native / reflective dependency functional.

# ---- JNI (libvips_android.so) ----
# The JNI entry points are looked up by exact method name; keep the class.
-keep class com.openconvert.app.domain.converter.VipsNative { *; }

# ---- FFmpegKit ----
# FFmpegKit loads native libs by name and uses reflection across its API surface.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.**

# ---- PdfBox-Android ----
# PdfBox uses reflection for COS object creation and resource loading.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ---- LiTr (video) ----
-keep class com.linkedin.android.litr.** { *; }
-dontwarn com.linkedin.android.litr.**

# ---- Media3 ----
# Media3 Transformer / Codec are referenced reflectively by the platform.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- Room / WorkManager ----
# Room generated implementations are looked up by name.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
# WorkManager uses reflection on Worker classes.
-keep class com.openconvert.app.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# ---- Compose / Navigation ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Apache Commons Compress ----
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# ---- LibreOfficeKit ----
-keep class org.libreoffice.kit.** { *; }
-keep class com.openconvert.app.domain.converter.OfficeEngine { *; }
-keep class com.openconvert.app.domain.converter.OfficeConverter { *; }
-dontwarn org.libreoffice.kit.**

# ---- Model classes kept for Room mapping + payload codec ----
-keep class com.openconvert.app.domain.model.** { *; }
-keep class com.openconvert.app.data.local.** { *; }

# ---- General ----
-dontwarn org.slf4j.**
-dontwarn javax.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
