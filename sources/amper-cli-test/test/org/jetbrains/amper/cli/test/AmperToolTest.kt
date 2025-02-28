/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertTrue

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperToolTest : AmperCliTestBase() {

    @Test
    fun `tool jdk jstack runs`() = runSlowTest {
        val p = tempRoot.resolve("new").also { it.createDirectories() }
        val result = runCli(p, "tool", "jdk", "jstack", ProcessHandle.current().pid().toString())

        val requiredSubstring = "Full thread dump"
        assertTrue("stdout should contain '$requiredSubstring':\n${result.stdout}") {
            result.stdout.contains(requiredSubstring)
        }
    }
}