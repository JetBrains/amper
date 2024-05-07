/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        mavenCentral()
        // add repositories:
        google()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/amper/amper")
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
}

plugins {
    // apply the plugin:
    id("org.jetbrains.amper.settings.plugin").version("0.3.0-dev-558")
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

rootProject.name = "my-project-name"

include(":lib")
include(":app")


