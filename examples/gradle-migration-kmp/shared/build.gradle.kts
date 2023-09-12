plugins {
    // Apply any plugins as usual.
    // Note: The following plugins are preconfigured and their versions can't be changed:
    //   * `org.jetbrains.kotlin.multiplatform`
    //   * `org.jetbrains.kotlin.android`  ¡
    //   * `com.android.library`
    //   * `com.android.application`
    //   * `org.jetbrains.compose`
    //
    // The following code:
    //   `kotlin("multiplatform") version 1.9.0`
    // should be replaced with:
    //   `kotlin("multiplatform")`
    //
    // Note: currently kotlin("jvm) plugin not supported in Pot modules.
    // Replace
    //    `kotlin("jvm)`
    // with
    //    `kotlin("multiplatform")`

    id("com.android.library")
    kotlin("multiplatform")
}

kotlin {
    // Targets are defined in Pot.yaml.
    // The following code should be removed from the Gradle build script:
    //
    //    targets {
    //        jvm()
    //        androidTarget()
    //    }

    // Access source sets configured in the Pot:
    sourceSets {
        val jvmMain by getting {
            // Configure the source set here
        }
    }
}

// Configure existing tasks or plugins
android {
    namespace = "com.example"
    compileSdkVersion = "android-34"
}
