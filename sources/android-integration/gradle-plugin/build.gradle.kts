/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `java-gradle-plugin`
}

amperGradlePlugin()

gradlePlugin {
    plugins {
        create("amperGradleAndroidPlugin") {
            id = "org.jetbrains.amper.android.settings.plugin"
            implementationClass = "AmperAndroidIntegrationSettingsPlugin"
        }
    }
}