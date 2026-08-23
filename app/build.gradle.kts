import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * Release 签名凭据来源（优先级从高到低）：
 *   1. `signing.properties`（仓库根目录，已在 .gitignore 中）
 *   2. 环境变量 OPENCONVERT_STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD（CI 用）
 *
 * 两者都不存在时 release 构建**不配置签名**（产出 unsigned APK）而非用硬编码
 * 口令——凭据绝不进版本库。见 signing.properties.example。
 */
val signingProps = Properties()
val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) {
    signingPropsFile.inputStream().use { stream -> signingProps.load(stream) }
}

fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key)?.takeIf { value -> value.isNotBlank() }
        ?: System.getenv(env)?.takeIf { value -> value.isNotBlank() }

val releaseStorePassword = signingValue("storePassword", "OPENCONVERT_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "OPENCONVERT_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "OPENCONVERT_KEY_PASSWORD")
val releaseKeystore = rootProject.file(
    signingProps.getProperty("storeFile") ?: "app/openconvert-release.jks",
)
val hasReleaseSigning = releaseKeystore.exists() &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null
val requireReleaseSigning =
    providers.gradleProperty("openconvertRequireReleaseSigning").orNull == "true"
if (requireReleaseSigning && !hasReleaseSigning) {
    error(
        "OpenConvert: release signing required " +
            "(-PopenconvertRequireReleaseSigning=true) but keystore/credentials are missing",
    )
}
val releaseInstrumentationEnabled =
    providers.gradleProperty("openconvertTestBuildType").orNull == "release"
val emulatorAbi = providers.gradleProperty("openconvertEmulatorAbi").orNull
val ciSmoke = providers.gradleProperty("openconvertCiSmoke").orNull == "true"
val openConvertVersionName = providers.gradleProperty("OPENCONVERT_VERSION_NAME")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: error("OPENCONVERT_VERSION_NAME missing from gradle.properties")
val openConvertVersionCode = providers.gradleProperty("OPENCONVERT_VERSION_CODE")
    .orNull
    ?.trim()
    ?.toIntOrNull()
    ?: error("OPENCONVERT_VERSION_CODE missing or not an int")

android {
    namespace = "com.openconvert.app"
    compileSdk = 36

    // 默认仍验证 debug；升级验收通过命令行切到 release，使测试 APK 可以直接
    // 驱动同签名的 Basic / Office 正式安装包。
    testBuildType = providers.gradleProperty("openconvertTestBuildType").orNull ?: "debug"

    defaultConfig {
        applicationId = "com.openconvert.app"
        minSdk = 26
        targetSdk = 36
        versionCode = openConvertVersionCode
        versionName = openConvertVersionName
        buildConfigField("int", "VERSION_CODE_BASE", openConvertVersionCode.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        if (ciSmoke) {
            // GitHub-hosted emulator is x86_64 and has no Office/vips native.
            // Only run tests that stay in Java/Kotlin + Room + Compose.
            testInstrumentationRunnerArguments["class"] = listOf(
                "com.openconvert.app.ui.StringsCatalogInstrumentedTest",
                "com.openconvert.app.ui.AccessibilitySemanticsInstrumentedTest",
                "com.openconvert.app.ui.FileDrivenFlowInstrumentedTest",
                "com.openconvert.app.ui.TaskCenterInstrumentedTest",
                "com.openconvert.app.domain.converter.ArchiveConverterInstrumentedTest",
                "com.openconvert.app.domain.converter.OfficePackIsolationInstrumentedTest",
                "com.openconvert.app.domain.work.ConversionRecoveryInstrumentedTest",
                "com.openconvert.app.data.PresetStoreInstrumentedTest",
            ).joinToString(",")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    // 阶段 09：把 170+ MiB 的 LibreOfficeKit 从默认发行包剥离。
    // basic 是主应用轻量版；office 使用同一 applicationId / 签名，可作为原地升级包，
    // 并从 src/office 打入 LOKit native 库与运行资源。
    flavorDimensions += "edition"
    productFlavors {
        create("basic") {
            dimension = "edition"
            buildConfigField("boolean", "OFFICE_BUNDLED", "false")
        }
        create("office") {
            dimension = "edition"
            versionCode = openConvertVersionCode + 1
            versionNameSuffix = "-office"
            buildConfigField("boolean", "OFFICE_BUNDLED", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            testProguardFiles("test-proguard-rules.pro")
            if (releaseInstrumentationEnabled) {
                proguardFile("release-instrumentation-rules.pro")
            }
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "OpenConvert: no signing credentials found " +
                        "(signing.properties / OPENCONVERT_* env) — release APK will be UNSIGNED.",
                )
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Release and local debug stay arm64-v8a. CI emulator sets
    // -PopenconvertEmulatorAbi=x86_64 so GitHub-hosted AVD can install the APK.
    splits {
        abi {
            isEnable = true
            reset()
            include(if (emulatorAbi == "x86_64") "x86_64" else "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Only drop the colliding short stubs shipped by older AndroidX AARs.
        // Do not exclude META-INF/NOTICE, META-INF/LICENSE*, or assets/THIRD_PARTY_NOTICES.md.
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    // Room MigrationTestHelper 从 androidTest assets 读导出的 schema JSON。
    // 不加这一行会报 "Cannot find the schema file in the assets folder"。
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // audio covers every encoder we actually invoke (lame/opus/vorbis/aac/flac/mpeg4).
    // full also ships libvpx/dav1d/kvazaar/gnutls we no longer need; WEBM is LiTr-only.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
    implementation("com.linkedin.android.litr:litr:1.5.7")
    implementation("androidx.media3:media3-transformer:1.11.0")
    // The maintained FFmpegKit AAR currently omits this runtime dependency
    // from its published POM, although its Java API still calls it.
    implementation("com.arthenica:smart-exception-java:0.2.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20250107")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.work:work-testing:2.10.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register("verifyOpenConvertVersion") {
    group = "verification"
    description = "Fail if version properties are invalid or a v* tag does not match VERSION_NAME"
    doLast {
        require(openConvertVersionName.matches(Regex("""^\d+\.\d+\.\d+$"""))) {
            "OPENCONVERT_VERSION_NAME must be x.y.z, got '$openConvertVersionName'"
        }
        require(openConvertVersionCode > 100) {
            "OPENCONVERT_VERSION_CODE must be > 100 (v1.0 was 100), got $openConvertVersionCode"
        }
        val ref = System.getenv("GITHUB_REF").orEmpty()
        if (ref.startsWith("refs/tags/v")) {
            val tag = System.getenv("GITHUB_REF_NAME").orEmpty().removePrefix("v")
            require(tag == openConvertVersionName) {
                "Git tag v$tag != OPENCONVERT_VERSION_NAME $openConvertVersionName"
            }
        }
        logger.lifecycle(
            "OpenConvert version $openConvertVersionName " +
                "(Basic=$openConvertVersionCode Office=${openConvertVersionCode + 1})",
        )
    }
}

