/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kotlin

import org.gradle.api.tasks.TaskProvider
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Configures this compilation task with the Kotlin compiler options from the given [amperSettings].
 * This does not include language settings that can be set on source sets directly.
 */
fun TaskProvider<out KotlinCompilationTask<*>>.configureCompilerOptions(amperSettings: Settings) {
    configure {
        it.compilerOptions.apply {
            configureFrom(amperSettings)
        }
    }
}

/**
 * Configures these Kotlin compiler options according to the given [amperSettings].
 * This does not include language settings that can be set on source sets directly.
 */
fun KotlinCommonCompilerOptions.configureFrom(amperSettings: Settings) {
    allWarningsAsErrors.set(amperSettings.kotlin.allWarningsAsErrors)
    freeCompilerArgs.addAll(amperSettings.kotlin.freeCompilerArgs ?: emptyList())
    suppressWarnings.set(amperSettings.kotlin.suppressWarnings)
    verbose.set(amperSettings.kotlin.verbose)

    if (this is KotlinJvmCompilerOptions) {
        val jvmRelease = amperSettings.jvm.release
        if (jvmRelease != null) {
            // we have to override -jvm-target here, otherwise defaults get in the way
            jvmTarget.set(JvmTarget.fromTarget(jvmRelease.legacyNotation))
            // we use the legacyNotation here (not releaseNumber) even though -Xjdk-release supports "8", because of KT-66560
            // (passing "-Xjdk-release=8 -jvm-target 1.8" to kotlinc incorrectly fails, so we pass 1.8)
            freeCompilerArgs.add("-Xjdk-release=${jvmRelease.legacyNotation}")
        }
    }
}
