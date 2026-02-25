/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.settings.reference

import org.jetbrains.amper.plugins.TaskAction

@TaskAction
fun checkSettings(
    kotlinVersion: String,
    kotlinLanguageVersion: String?,
    kotlinWarningsAsErrors: Boolean,
    jvmRelease: Int,
    jvmRuntimeClasspathMode: String,
    jdkVersion: String,
    junitVersion: String,
    group: String?,
    name: String?,
    version: String?,
    kotlinArgs: List<String>?,
) {
    println("kotlinVersion: $kotlinVersion")
    println("kotlinLanguageVersion: $kotlinLanguageVersion")
    println("kotlinWarningsAsErrors: $kotlinWarningsAsErrors")
    println("jvmRelease: $jvmRelease")
    println("jvmRuntimeClasspathMode: $jvmRuntimeClasspathMode")
    println("jdkVersion: $jdkVersion")
    println("junitVersion: $junitVersion")
    println("publishingGav: $group:$name:$version")
    println("kotlinArgs: $kotlinArgs")
}
