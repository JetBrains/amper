/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.time.Duration


internal val knownGradleFiles = setOf(
    "gradle-wrapper.jar",
    "gradle-wrapper.properties",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "build.gradle.kts",
    "settings.gradle.kts",
)

@OptIn(ExperimentalPathApi::class)
internal fun Path.deleteGradleFiles() {
    walk()
        .filter { it.name in knownGradleFiles }
        .forEach { it.deleteExisting() }
}


fun runTestInfinitely(testBody: suspend TestScope.() -> Unit): TestResult = runTest(timeout = Duration.INFINITE, testBody = testBody)