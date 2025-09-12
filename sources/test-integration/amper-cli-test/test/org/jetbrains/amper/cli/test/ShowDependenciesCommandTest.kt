/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
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
class ShowDependenciesCommandTest : AmperCliTestBase(), GoldenTest {

    // FIXME this is not the build dir. Why are we doing this?
    override fun buildDir(): Path = tempRoot

    @Test
    fun `show dependencies for jvm module - default`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root",
        )

        CliTestRun("jvm-exported-dependencies_root_default", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for jvm module with --include-tests`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--include-tests",
        )

        CliTestRun("jvm-exported-dependencies_root_tests", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for jvm module with group common`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platforms=common",
        )

        CliTestRun("jvm-exported-dependencies_root_common", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for jvm module with inexistent platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platforms=notaplatform",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: The following platforms are unresolved: notaplatform.
            
            Module root target platforms: jvm
        """.trimIndent())
    }

    @Test
    fun `show dependencies for jvm module with undeclared platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platforms=ios",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("ERROR: Module root doesn't support platforms: ios.")
    }

    @Test
    fun `show dependencies for multiplatform module - default`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies",
        )

        CliTestRun("multiplatform-lib-with-alias_default", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with --include-tests`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--include-tests",
        )

        CliTestRun("multiplatform-lib-with-alias_tests", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group common`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platforms=common",
        )

        CliTestRun("multiplatform-lib-with-alias_common", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group native`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platforms=native",
        )

        CliTestRun("multiplatform-lib-with-alias_native", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group jvm`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platforms=jvm",
        )

        CliTestRun("multiplatform-lib-with-alias_jvm", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with inexistent platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platforms=notaplatform",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: The following platforms are unresolved: notaplatform.
            
            Module multiplatform-lib-with-alias target platforms: jvm, android, iosArm64, iosSimulatorArm64, iosX64
        """.trimIndent())
    }
}

private class CliTestRun(
    caseName: String,
    override val base: Path,
    private val cliResult: AmperCliResult
) : BaseTestRun(caseName) {
    override fun GoldenTest.getInputContent(inputPath: Path): String = cliResult.stdoutClean

    override fun GoldenTest.getExpectContent(expectedPath: Path): String {
        // This is the actual check.
        if (!expectedPath.exists()) expectedPath.writeText("")
        return readContentsAndReplace(expectedPath, base)
    }
}
