plugins {
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