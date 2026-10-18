plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apter.robolectric.junit5)
}

kotlin {
    jvmToolchain(17)
}

// In-app build tag (ENC-69): the status screen shows the short SHA of the
// commit the build was made from, so any installed build can be identified
// exactly from inside the app. Read through the exec provider so it stays
// configuration-cache compatible; falls back to "dev" when git or the
// checkout is unavailable.
val gitShortSha: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "dev" }
} catch (exception: Exception) {
    "dev"
}

android {
    namespace = "com.shippopotamus.bloviate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shippopotamus.bloviate"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Data-layer classes log to logcat (a deliberate on-device
            // validation surface); on the JVM those android.jar stubs must
            // no-op instead of throwing "not mocked".
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Robolectric for framework-bound logic (clipboard/insertion) per
    // contracts/UNIT_TESTING.md §2; pins mirror Enclave's catalog.
    testImplementation(libs.robolectric)
    testImplementation(libs.apter.junit5.robolectric.extension)

    // whisper-android, consumed via composite build (see settings.gradle.kts): the
    // core module carries the WhisperEngine API; the optional Hilt module provides
    // its binding. Both coordinates are substituted with included-build projects.
    implementation(libs.whisper.android)
    implementation(libs.whisper.android.hilt)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
