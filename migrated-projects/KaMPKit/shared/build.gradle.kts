@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("native.cocoapods")
    id("app.cash.sqldelight")
    // TODO Enable after skie become compatible.
//    id("co.touchlab.skie")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
    }
}

kotlin {
    cocoapods {
        summary = "Common library for the KaMP starter kit"
        homepage = "https://github.com/touchlab/KaMPKit"
        framework {
            isStatic = false // SwiftUI preview requires dynamic framework
            linkerOpts("-lsqlite3")
            export(libs.touchlab.kermit.simple)
        }
        extraSpecAttributes["swift_version"] = "\"5.0\"" // <- SKIE Needs this!
        podfile = project.file("../ios/Podfile")
    }
}

sqldelight {
    databases.create("KaMPKitDb") {
        srcDirs("sqldelight")
        packageName.set("co.touchlab.kampkit.db")
    }
}
