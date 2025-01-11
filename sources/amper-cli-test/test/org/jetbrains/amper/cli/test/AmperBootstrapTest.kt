/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.test.Test

class AmperBootstrapTest : AmperCliTestBase() {

    override val testDataRoot: Path
        get() = error("testDataRoot shouldn't be used")

    @Test
    fun `amper can build itself`() {
        runBlocking {
            // We don't want to run the 'test' command because that would amount to run the whole test suite twice.
            // Testing that the project compiles with the new sources is good enough, as the tests will be covered
            // in the build of the MR that bumps Amper in Amper.
            runCli(projectRoot = Dirs.amperCheckoutRoot, "build")
        }
    }
}
