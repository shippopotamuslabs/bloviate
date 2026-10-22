pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bloviate"
include(":app")
include(":core:domain")
include(":core:application")
include(":core:data")

// whisper-android lives in its own repo (browntown.development-group/whisper-android),
// vendored as a submodule and consumed via composite build (same pattern as Enclave).
// The Maven coordinates are substituted with the included build's projects during
// development; releases will consume the published AARs instead.
includeBuild("third_party/whisper-android") {
    dependencySubstitution {
        substitute(module("com.shippopotamuslabs:whisper-android"))
            .using(project(":whisper-android"))
        substitute(module("com.shippopotamuslabs:whisper-android-hilt"))
            .using(project(":whisper-android-hilt"))
    }
}
