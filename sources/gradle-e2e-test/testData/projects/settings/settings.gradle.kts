/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
    includeBuild("../../../../..") // <REMOVE_LINE_IF_RUN_WITH_PLUGIN_CLASSPATH>
}

plugins {
    id("org.jetbrains.amper.settings.plugin")
}

include(":subModule")
