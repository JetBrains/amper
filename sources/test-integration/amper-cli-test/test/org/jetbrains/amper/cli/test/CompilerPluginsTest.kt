/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

@Execution(ExecutionMode.CONCURRENT)
class CompilerPluginsTest : AmperCliTestBase() {

    @Test
    fun koin() = runSlowTest {
        val projectRoot = testProject("compiler-plugin-koin")
        val result = runCli(projectRoot = projectRoot, "run")
        result.assertStdoutContains("Hello 'Alice' (alice@example.com)!")
    }

    @Test
    fun koin_test() = runSlowTest {
        val projectRoot = testProject("compiler-plugin-koin")
        // need -XX:+EnableDynamicAgentLoading to remove the bytebuddy warning and have empty stderr
        // need -Xshare:off to remove the warning about class data sharing not being usable (caused by the agent AFAIU)
        runCli(projectRoot = projectRoot, "test", "--jvm-args=-XX:+EnableDynamicAgentLoading", "--jvm-args=-Xshare:off")
    }

    @Test
    fun metro() = runSlowTest {
        val projectRoot = testProject("compiler-plugin-metro")
        val result = runCli(projectRoot = projectRoot, "run")
        result.assertStdoutContains("Hourly forecast")
    }
}
