plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jetbrains.sample.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jetbrains.sample.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testBuildType =  "debug"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.jetbrains.sample.app.test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.tracing:tracing")
    androidTestImplementation ("androidx.test.ext:junit-ktx:1.1.5")
    androidTestImplementation ("androidx.test.uiautomator:uiautomator:2.3.0")
}

tasks.register("createDebugAndroidTestApk") {
    dependsOn("assembleDebugAndroidTest")

    doLast {
        val outputDir = file("$buildDir/outputs/apk/androidTest/debug/")
        val apkFiles = outputDir.listFiles { _, name ->
            name.endsWith(".apk")
        }

        if (apkFiles != null && apkFiles.isNotEmpty()) {
            println("Debug Android Test APK created at: ${apkFiles[0].absolutePath}")
        } else {
            println("Debug Android Test APK not found.")
        }
    }
}
