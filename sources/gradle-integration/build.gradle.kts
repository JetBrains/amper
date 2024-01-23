/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("java-gradle-plugin")
}

amper.useAmperLayout = true

amperGradlePlugin()

gradlePlugin {
    plugins {
        create("amperProtoSettingsPlugin") {
            id = "org.jetbrains.amper.settings.plugin"
            implementationClass = "org.jetbrains.amper.gradle.BindingSettingsPlugin"
        }
    }
}

kotlin {
    (sourceSets.findByName("jvm") ?: sourceSets.findByName("jvmMain"))?.apply {
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-serialization") {
                version {
                    // Should be replaced by synchVersions.sh
                    /*kotlin_magic_replacement*/ strictly("1.9.20")
                }
            }
        }
    }
}
