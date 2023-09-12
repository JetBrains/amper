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
    targets {
        androidTarget()
    }

    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Use a Pot as a dependency
                implementation(project(":shared"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.foundation:foundation:1.4.1")
                implementation("org.jetbrains.compose.material3:material3:1.4.1")
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