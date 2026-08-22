# Applied only when -PopenconvertTestBuildType=release is present. AndroidJUnitRunner executes
# inside the target process and resolves these direct dependencies across the target/test APK
# class loaders. Normal production Release builds do not use this file.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.openconvert.app.** { *; }
