@file:Suppress("UnstableApiUsage")

include(":hifitrack")


include(":hificore")


pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "Gramophone"
/*includeBuild(file("media3").toPath().toRealPath().toAbsolutePath().toString()) {
    dependencySubstitution {
        substitute(module("androidx.media3:media3-common")).using(project(":lib-common"))
        substitute(module("androidx.media3:media3-common-ktx")).using(project(":lib-common-ktx"))
        substitute(module("androidx.media3:media3-exoplayer")).using(project(":lib-exoplayer"))
        substitute(module("androidx.media3:media3-exoplayer-midi")).using(project(":lib-decoder-midi"))
        substitute(module("androidx.media3:media3-session")).using(project(":lib-session"))
    }
}*/
include(":libPhonograph", ":app", ":baselineprofile")
