/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.helper.cliTest
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class DependenciesCommandTest : AmperCliTestBase() {

    override fun baseTestResourcesPath(): Path = super.baseTestResourcesPath() / "dependencies"

    @Test
    fun `show dependencies command help prints dash dash`() = runSlowTest {
        val r = runCli(projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root")

        Charset.availableCharsets().forEach { println(it.key) }

        cliTest("jvm-exported-dependencies-root", cliResult = r)
    }
}

