// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val agpVersion = "8.13.0"
    id("com.android.application") version agpVersion apply false
	id("com.android.library") version agpVersion apply false
	id("com.android.test") version agpVersion apply false
	id("androidx.baselineprofile") version "1.4.0" apply false
    val kotlinVersion = "2.2.20-RC2"
	kotlin("android") version kotlinVersion apply false
    kotlin("plugin.parcelize") version kotlinVersion apply false
    kotlin("plugin.compose") version kotlinVersion apply false
    //noinspection NewerVersionAvailable false positive
    id("com.mikepenz.aboutlibraries.plugin.android") version "13.0.0-b01" apply false
    id("com.osacky.doctor") version "0.12.1"
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
