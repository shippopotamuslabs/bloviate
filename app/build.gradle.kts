import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apter.robolectric.junit5)
    alias(libs.plugins.google.services)
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

// Release signing: gitignored root `keystore.properties` (see keystore.properties.example).
// Without it, release builds fall back to the debug signing config.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }
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

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // FirebaseAuth for the auth providers (ENC-197); the session
    // implementation itself stays in :core:data.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Credential Manager exception types for the sign-in error mapper
    // (ENC-197); the session itself lives in :core:data.
    implementation(libs.androidx.credentials)

    // sherpa-onnx lives with the Parakeet engine in :core:data (ENC-169):
    // see core/data/libs/README.md.

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
