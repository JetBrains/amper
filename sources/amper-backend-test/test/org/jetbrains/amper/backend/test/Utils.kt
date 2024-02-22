/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

fun runTestInfinitely(testBody: suspend TestScope.() -> Unit): TestResult = runTest(timeout = Duration.INFINITE, testBody = testBody)
