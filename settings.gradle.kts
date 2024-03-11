/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

buildscript {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()

        // For dev versions of KMP Gradle plugins
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

        // For locally published plugin versions
        mavenLocal()

        // For published version
        maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")

        // For idea api dependencies
        maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
        maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")

        // Create local.properties.
        rootDir.resolve("local.properties").also {
            if (!it.exists()) {
                it.writeText("scratch.username=\nscratch.password=")
            }
        }
    }

    dependencies {
        // !!! Use syncVersions.sh to update these versions
        classpath("org.jetbrains.amper.settings.plugin:gradle-integration:0.2.2")
    }
}

plugins.apply("org.jetbrains.amper.settings.plugin")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.4.0")
}
