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
            "show", "dependencies", "--module", "root", "--platform-group=common",
        )

        CliTestRun("jvm-exported-dependencies_root_common", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for jvm module with inexistent platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=notaplatform",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: Invalid platform group name 'notaplatform'.
            
            Supported platform groups for module 'root': common, jvm
        """.trimIndent())
    }

    @Test
    fun `show dependencies for jvm module with platform typo (single choice)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=commno",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: Invalid platform group name 'commno'. Did you mean common?
            
            Supported platform groups for module 'root': common, jvm
        """.trimIndent())
    }

    @Test
    fun `show dependencies for jvm module with undeclared platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=ios",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("ERROR: Module 'root' doesn't support platform 'ios'")
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
            "show", "dependencies", "--platform-group=common",
        )

        CliTestRun("multiplatform-lib-with-alias_common", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group native`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=native",
        )

        CliTestRun("multiplatform-lib-with-alias_native", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group jvm`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=jvm",
        )

        CliTestRun("multiplatform-lib-with-alias_jvm", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with group from alias`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=jvmAndAndroid",
        )

        CliTestRun("multiplatform-lib-with-alias_alias_jvmAndAndroid", base = Path("testResources/dependencies"), cliResult = r).doTest()
    }

    @Test
    fun `show dependencies for multiplatform module with inexistent platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=notaplatform",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: Invalid platform group name 'notaplatform'.

            Supported platform groups for module 'multiplatform-lib-with-alias': android, apple, common, ios, iosArm64, iosSimulatorArm64, iosX64, jvm, jvmAndAndroid, native
        """.trimIndent())
    }

    @Test
    fun `show dependencies for multiplatform module with platform typo ios64 (multi choice)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=ios64",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: Invalid platform group name 'ios64'. Did you mean one of ios, iosArm64, iosX64?

            Supported platform groups for module 'multiplatform-lib-with-alias': android, apple, common, ios, iosArm64, iosSimulatorArm64, iosX64, jvm, jvmAndAndroid, native
        """.trimIndent())
    }

    @Test
    fun `show dependencies for multiplatform module with platform typo nadroive (multi choice)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=nadroive",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("""
            ERROR: Invalid platform group name 'nadroive'. Did you mean one of android, native?

            Supported platform groups for module 'multiplatform-lib-with-alias': android, apple, common, ios, iosArm64, iosSimulatorArm64, iosX64, jvm, jvmAndAndroid, native
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
