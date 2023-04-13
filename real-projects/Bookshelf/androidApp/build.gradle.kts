plugins {
    id("com.android.application")
    kotlin("android")
}

// https://maven.google.com/web/index.html?q=compiler#androidx.compose.compiler:compiler
val compose_compiler_version = "1.3.2"
// https://maven.google.com/web/index.html?q=ui#androidx.compose.ui:ui
val compose_ui_version = "1.3.0-rc01"

repositories {
    google()
    mavenCentral()
    // Only required for realm-kotlin snapshots
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.compose.compiler:compiler:${compose_compiler_version}")
    compileOnly("io.realm.kotlin:library-base:${rootProject.extra["realmVersion"]}")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.compose.ui:ui:$compose_ui_version")
    implementation("androidx.compose.material:material:$compose_ui_version")
    implementation("androidx.compose.ui:ui-tooling:$compose_ui_version")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.activity:activity-compose:1.6.0")
    implementation("androidx.navigation:navigation-runtime-ktx:2.5.2")
    implementation("androidx.navigation:navigation-compose:2.6.0-alpha02")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1")
}

android {
    signingConfigs {
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
        }
        // TODO create key for Playstore
//        create("release") {
//            keyAlias = "release"
//            keyPassword = "my release key password"
//            storeFile = file("release.keystore")
//            storePassword = "my keystore password"
//        }
    }
    compileSdk = 33
    defaultConfig {
        applicationId = "io.realm.sample.bookshelf.android"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = compose_compiler_version
    }
}
