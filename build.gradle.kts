plugins {
    id("com.android.application") version "8.12.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
}

// 阶段 09：使用与 AGP 同代的 bundletool 复现设备 split 下载体积。
// 示例：./gradlew runBundletool -PbundletoolArgs="version"
val bundletool by configurations.creating

dependencies {
    add(bundletool.name, "com.android.tools.build:bundletool:1.18.1")
    // bundletool 的 get-device-spec 路径会进入 Kotlin 编写的 sdklib；其发布 POM
    // 未传递声明 stdlib，显式补齐以避免 kotlin.jvm.internal.Intrinsics 缺失。
    add(bundletool.name, "org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
}

tasks.register<JavaExec>("runBundletool") {
    group = "verification"
    description = "Run bundletool with arguments from -PbundletoolArgs"
    classpath = bundletool
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    providers.gradleProperty("bundletoolArgs").orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.split(Regex("\\s+"))
        ?.let(::args)
}
