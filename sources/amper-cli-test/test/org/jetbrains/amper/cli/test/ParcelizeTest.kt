/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ParcelizeTest : AmperCliTestBase() {

    @Test
    fun `parcelize android lib - build`() = runSlowTest {
        runCli(testProject("parcelize-android-lib"), "build")
    }

    @Test
    fun `parcelize android lib - test`() = runSlowTest {
        runCli(testProject("parcelize-android-lib"), "test")
    }

    @Test
    fun `parcelize android app - build`() = runSlowTest {
        runCli(testProject("parcelize-android-app"), "build")
    }

    @Test
    fun `parcelize with shared kmp model`() = runSlowTest {
        runCli(testProject("parcelize-shared-kmp-model"), "build")
    }
}
