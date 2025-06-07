// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val agpVersion = "8.11.0-rc01"
    id("com.android.application") version agpVersion apply false
	id("com.android.library") version agpVersion apply false
	id("com.android.test") version agpVersion apply false
	id("androidx.baselineprofile") version "1.3.4" apply false
    val kotlinVersion = "2.0.21" // https://youtrack.jetbrains.com/issue/KT-77988
	kotlin("android") version kotlinVersion apply false
    kotlin("plugin.parcelize") version kotlinVersion apply false
    kotlin("plugin.compose") version kotlinVersion apply false
    id("com.mikepenz.aboutlibraries.plugin") version "12.2.1" apply false
    id("com.osacky.doctor") version "0.11.0"
}

doctor {
    javaHome {
        ensureJavaHomeMatches.set(false)
        ensureJavaHomeIsSet.set(false)
    }
}

tasks.withType(JavaCompile::class.java) {
    options.compilerArgs.add("-Xlint:all")
}