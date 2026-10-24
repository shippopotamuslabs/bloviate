plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.shippopotamus.bloviate.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    // Custom-styles account cache (BLO-27 S2): JSON record encoding.
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.crashlytics)

    // Google Sign-In via Credential Manager (ENC-197): pins mirror
    // Stratum's catalog exactly.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // whisper-android, consumed via composite build (see settings.gradle.kts);
    // coordinates are substituted with the included-build projects.
    implementation(libs.whisper.android)

    // sherpa-onnx (Parakeet engine, ENC-54): not published to Maven Central
    // under an official coordinate, so the official release AAR is vendored.
    // Version, source URL, SHA-256, and licenses: libs/README.md.
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
