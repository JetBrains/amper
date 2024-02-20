/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    }
    includeBuild("../../../../..") // <REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>
}

plugins {
    id("org.jetbrains.amper.settings.plugin")
}

// we want to check that this is respected in non-amper modules
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
    }
}

include("app-jvm")
include("lib-multiplatform")
