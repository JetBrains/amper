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
                // from the fake maven repo, declared in settings.gradle.kts in the dependencyResolutionManagement block
                implementation("com.example.unique:my-unique-dep:1.0.0")
            }
        }
    }
}
