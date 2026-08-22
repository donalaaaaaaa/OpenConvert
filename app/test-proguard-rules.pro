# Release instrumentation runs inside the minified target process. Keep the runner's direct
# dependencies and test entry points by their original names; Android resolves them across the
# target/test APK class loaders before JUnit starts.
-keep class androidx.test.** { *; }
-keep class androidx.tracing.** { *; }
-keep class org.junit.** { *; }
-keep class org.hamcrest.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class com.openconvert.app.** { *; }
