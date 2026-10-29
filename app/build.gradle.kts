import java.util.Properties

val kiroxProperties = Properties().apply {
    val file = rootProject.file("kirox.properties")
    if (file.exists()) file.inputStream().use(::load)
}
/** First non-blank value among [names] from kirox.properties or the environment. */
fun secret(vararg names: String): String =
    names.firstNotNullOfOrNull { name -> (kiroxProperties.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Firebase push is configured only when app/google-services.json is present (not committed).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    // releaseCheck (package com.kirox.app.check) is a local R8 check build without Firebase:
    // google-services.json has no client for it, so its config task is skipped.
    tasks.matching { it.name == "processReleaseCheckGoogleServices" }.configureEach { enabled = false }
}

android {
    // Native libraries (TDLib, call engine) are ~50 MB raw and ~20 MB compressed: compress them in the
    // APK so downloads and updates are about half the size; Android unpacks them once on install.
    packaging { jniLibs { useLegacyPackaging = true } }
    namespace = "com.kirox.app"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.kirox.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 8
        versionName = "1.0.0-rc.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "TELEGRAM_API_ID", secret("KIROX_API_ID", "kirox.apiId").toIntOrNull()?.toString() ?: "0")
        buildConfigField("boolean", "TELEGRAM_USE_TEST_DC", "false")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${secret("KIROX_API_HASH", "kirox.apiHash").replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        // TDLib ships arm64 only; the call engine is built for the same ABI.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_static", "-DANDROID_ARM_NEON=TRUE") }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("../voip/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        // Release key lives outside the repository; its path and passwords come from kirox.properties.
        val storePath = secret("KIROX_RELEASE_STORE_FILE")
        if (storePath.isNotEmpty() && file(storePath).exists()) create("release") {
            storeFile = file(storePath)
            storePassword = secret("KIROX_RELEASE_STORE_PASSWORD")
            keyAlias = secret("KIROX_RELEASE_KEY_ALIAS")
            keyPassword = secret("KIROX_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Telegram test DC for safe auth testing: KIROX_USE_TEST_DC=true in kirox.properties. Never in release.
            buildConfigField("boolean", "TELEGRAM_USE_TEST_DC", (secret("KIROX_USE_TEST_DC") == "true").toString())
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // R8-optimized build signed with the local debug key and installed next to the normal app,
        // to check minification on a device without touching the real install or its session.
        create("releaseCheck") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".check"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Generated TDLib bindings (5 MB) exceed lint's file size limit; the code is not ours to fix.
        disable += "LintError"
        // Plain-text report, turned into CI annotations by the workflow.
        textReport = true
        textOutput = file("build/reports/lint-results-debug.txt")
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}


dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.google.libphonenumber)
    implementation(libs.zxing.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Video, GIF, video note and music playback (Apache-2.0).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // Animated TGS stickers (Lottie, Apache-2.0).
    implementation(libs.lottie.compose)
    // Video note recording: CameraX capture, Media3 square crop (both Apache-2.0).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.annotation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
