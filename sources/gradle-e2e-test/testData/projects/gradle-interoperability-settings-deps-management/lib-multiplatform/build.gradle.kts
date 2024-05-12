/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("multiplatform") //version "1.9.20"
}

kotlin {
    jvm()
    iosArm64()

    sourceSets {
        commonMain {
            dependencies {
                // only in https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental
                // which is declared in settings.gradle.kts in the dependencyResolutionManagement block
                implementation("io.ktor:ktor-client-core:3.0.0-wasm2")
            }
        }
    }
}
