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

android {
    namespace = "com.openconvert.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.openconvert.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    // ABI planning: P0 = arm64-v8a (modern phones). armeabi-v7a / x86_64 arrive later.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            useLegacyPackaging = true
        }
    }
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
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")
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
