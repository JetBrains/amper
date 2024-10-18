/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()

        // For dev versions of KMP Gradle plugins
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")

        // For locally published plugin versions. Please enable it manually
        // mavenLocal()

        // For published version
        maven("https://packages.jetbrains.team/maven/p/amper/amper")

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
}

plugins {
    id("org.jetbrains.amper.settings.plugin").version("0.5.0-dev-1866")
    id("com.gradle.enterprise").version("3.16.2")
    id("com.gradle.common-custom-user-data-gradle-plugin").version("2.0.2")
}

// important to have the correct root project name on CI for Gradle Enterprise, and for potentially other things
rootProject.name = "amper"

gradleEnterprise {
    buildScan {
        projectId = "amper"
        server = "https://ge.jetbrains.com"
        publishAlways()
        // background upload is bad for CI because the agent shutting down after the build could cut-off the upload
        isUploadInBackground = System.getenv("CI") == null
    }
}

include(":sources:amper-backend-test")
include(":sources:amper-cli-test")
include(":sources:amper-mobile-test")
include(":sources:amper-project-test")
include(":sources:amper-wrapper-test")
include(":sources:android-integration:android-sdk-detector")
include(":sources:android-integration:android-integration-core")
include(":sources:android-integration:dependency-resolution-android-extension")
include(":sources:android-integration:gradle-plugin")
include(":sources:android-integration:runner")
include(":sources:build-related:build-unpacked-dist")
include(":sources:build-related:build-zip-dist")
include(":sources:build-related:generate-build-properties")
include(":sources:build-related:tasks")
include(":sources:cli")
include(":sources:compose-resources")
include(":sources:concurrency")
include(":sources:core")
include(":sources:core-intellij")
include(":sources:dependency-resolution")
include(":sources:frontend-api")
include(":sources:frontend:dr")
include(":sources:frontend:plain:amper-psi")
include(":sources:frontend:plain:parserutil-stub")
include(":sources:frontend:plain:toml-psi")
include(":sources:frontend:plain:yaml-psi")
include(":sources:frontend:schema")
include(":sources:gradle-e2e-test")
include(":sources:gradle-integration")
include(":sources:test-base")
