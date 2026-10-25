import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kirox.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kirox.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        debug {
            // Telegram test DC for safe auth testing: KIROX_USE_TEST_DC=true in kirox.properties. Never in release.
            buildConfigField("boolean", "TELEGRAM_USE_TEST_DC", (secret("KIROX_USE_TEST_DC") == "true").toString())
        }
        release {
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.annotation:annotation:1.9.1")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
