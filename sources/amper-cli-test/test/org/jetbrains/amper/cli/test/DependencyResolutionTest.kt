/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.Test
import org.slf4j.event.Level

class DependencyResolutionTest : AmperCliTestBase() {

    @Test
    fun `jvm exported dependencies`() = runSlowTest {
        val result = runCli(testProject("jvm-exported-dependencies"), "run", "--module=cli")

        val find = "Process exited with exit code 0\n" +
                "STDOUT:\n" +
                "From Root Module + OneTwo"
        result.assertLogStartsWith(find, Level.INFO)
    }
}
