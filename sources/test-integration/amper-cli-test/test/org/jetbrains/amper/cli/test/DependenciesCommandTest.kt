/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.golden.BaseTestRun
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class DependenciesCommandTest : AmperCliTestBase(), GoldenTest {

    // FIXME this is not the build dir. Why are we doing this?
    override fun buildDir(): Path = tempRoot

    @Test
    fun `show dependencies command help prints dash dash`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root"
        )

        Charset.availableCharsets().forEach { println(it.key) }

        CliTestRun("jvm-exported-dependencies-root", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }
}

private class CliTestRun(
    caseName: String,
    override val base: Path,
    private val cliResult: AmperCliResult
) : BaseTestRun(caseName) {
    override fun GoldenTest.getInputContent(inputPath: Path): String {
        println(cliResult.stdout.replaceCliPrefix())
        return cliResult.stdout.replaceCliPrefix()
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path): String {
        // This is the actual check.
        if (!expectedPath.exists()) expectedPath.writeText("")
        return readContentsAndReplace(expectedPath, base)
    }

    private fun String.replaceCliPrefix(): String =
        this.replaceBefore("Dependencies of module root:", "")
}
