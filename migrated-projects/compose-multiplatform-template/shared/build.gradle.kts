import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    targets.withType(KotlinNativeTarget::class.java).configureEach { // WA for integration with iosApp
        binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }
}

