/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.kotlin

import org.gradle.api.tasks.TaskProvider
import org.jetbrains.amper.frontend.schema.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * Configures the JVM target and release compiler arguments for this Kotlin compilation task.
 */
fun TaskProvider<out KotlinCompilationTask<*>>.configureJvmTargetRelease(release: JavaVersion) {
    configure {
        it.compilerOptions.apply {
            if (this !is KotlinJvmCompilerOptions) return@apply
            // we have to override -jvm-target here, otherwise defaults get in the way
            jvmTarget.set(JvmTarget.fromTarget(release.legacyNotation))
            // we use the legacyNotation here (not releaseNumber) even though -Xjdk-release supports "8", because of KT-66560
            // (passing "-Xjdk-release=8 -jvm-target 1.8" to kotlinc incorrectly fails, so we pass 1.8) 
            freeCompilerArgs.add("-Xjdk-release=${release.legacyNotation}")
        }
    }
}
