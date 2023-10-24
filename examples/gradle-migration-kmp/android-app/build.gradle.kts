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

    id("com.android.application")
    id("org.jetbrains.compose")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Use a Deft Module as a dependency
                implementation(project(":shared"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation("androidx.activity:activity-compose:1.7.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
            }
        }
    }
}

android {
    namespace = "com.example"
    compileSdkVersion = "android-34"
    defaultConfig {
        minSdkPreview = "21"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}