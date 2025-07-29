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
        runCli(projectRoot = Dirs.amperCheckoutRoot, "build")
    }

    @Test
    fun `amper can launch smoke tests`() = runTest(timeout = 30.minutes) {
        // We want to run some tests because it can discover problems with the test mechanism/classpath etc.
        // We cannot run all tests because that would amount to running the whole test suite twice.
        // All other tests will be covered anyway in the build of the MR that bumps Amper in Amper.
        runCli(
            projectRoot = Dirs.amperCheckoutRoot,
            "test",
            "-m",
            "schema",
            "--include-classes=org.jetbrains.amper.frontend.schema.ParserKtTest"
        )
    }
}
