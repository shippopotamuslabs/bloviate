import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.apter.robolectric.junit5)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
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

// Derived versionCode (BLO-173; the rules live in contracts/RELEASE.md):
// the code is the first-parent commit count of HEAD, read through the
// exec provider so it stays configuration-cache compatible, the same
// pattern as gitShortSha above. Nobody hand-bumps this number. A build
// with no usable git history (a source export, a failed git call, an
// unparsable count) falls back to the committed fallback below. Note a
// shallow checkout still answers git, with a count of 1: release builds
// must run from a full-history checkout (the deploy workflow fetches
// with fetch-depth 0), and a code of 1 on a release build means the
// checkout was shallow, not that the fallback fired.
val fallbackVersionCode = 1
val derivedVersionCode: Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--first-parent", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
        ?.takeIf { it > 0 } ?: fallbackVersionCode
} catch (exception: Exception) {
    fallbackVersionCode
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
        versionCode = derivedVersionCode
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
    implementation(libs.firebase.crashlytics)

    // Glass Bars Gospel (BLO-2; UI_DESIGN_SPEC.md §6.9): Haze 2.0.1
    // for the glass top/bottom bars — core (source/effect) plus the
    // blur effect artifact (the `haze` umbrella does not include it).
    // No haze-glass: the Sheet token is spelled out with
    // HazeBlurStyle (2.x ships no Thick preset), and no hero surface
    // needs refraction.
    implementation(libs.haze)
    implementation(libs.haze.blur)

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

// Release verification (contracts/RELEASE.md): prints the version a
// build from this checkout would carry, so a release prep can check the
// derived code against `git rev-list --first-parent --count HEAD`
// without assembling anything.
// `./gradlew :app:printVersion` -> "versionName=1.0 versionCode=124"
// A typed task with @Input properties: a doLast closure over script
// values does not survive this project's configuration cache.
abstract class PrintVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()} versionCode=${versionCode.get()}")
    }
}

tasks.register<PrintVersionTask>("printVersion") {
    group = "verification"
    description = "Prints the versionName and derived versionCode for this checkout."
    versionName.set(android.defaultConfig.versionName)
    versionCode.set(derivedVersionCode)
}
