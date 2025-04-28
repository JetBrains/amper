/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.utils

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.cli.test.AmperCliTestBase
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

// Must not be made inline because it surfaces the Kotlin bug IDEA-370092
fun runSlowTest(testBody: suspend TestScope.() -> Unit): TestResult = runTest(timeout = 15.minutes, testBody = testBody)

// FIXME this should never be needed, because task output paths should be internal.
//  User-visible artifacts should be placed in user-visible directories (use some convention).
internal fun AmperCliTestBase.AmperCliResult.getTaskOutputPath(taskName: String): Path =
    buildOutputRoot / "tasks" / taskName.replace(':', '_')
