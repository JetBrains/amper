/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.test.Dirs
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class AmperBootstrapTest : AmperCliTestBase() {

    @Test
    fun `amper can build itself`() = runTest(timeout = 30.minutes) {
        // We don't want to run the 'test' command because that would amount to run the whole test suite twice.
        // Testing that the project compiles with the new sources is good enough, as the tests will be covered
        // in the build of the MR that bumps Amper in Amper.
        runCli(projectRoot = Dirs.amperCheckoutRoot, "build")
    }
}
