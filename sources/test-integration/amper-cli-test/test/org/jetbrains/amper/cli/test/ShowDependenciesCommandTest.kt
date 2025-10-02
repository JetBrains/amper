/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.assertStderrContains
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.AmperCliResult
import org.jetbrains.amper.test.golden.GoldFileTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.io.path.Path
import kotlin.test.Test

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class ShowDependenciesCommandTest : AmperCliTestBase() {

    private fun AmperCliResult.checkGold(caseName: String) = GoldFileTest(
        caseName = caseName,
        base = Path("testResources/dependencies"),
    ) { stdoutClean }.doTest()

    @Test
    fun `--module cannot be used with --all-modules`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--all-modules",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains("Error: option --module cannot be used with --all-modules")
    }

    @Test
    fun `show dependencies for jvm - single module default platforms`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root",
        )

        r.checkGold("jvm-exported-dependencies_root_default")
    }

    @Test
    fun `show dependencies for jvm - multiple modules default platforms`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module=root", "--module=cli",
        )

        r.checkGold("jvm-exported-dependencies_rootAndCli_default")
    }

    @Test
    fun `show dependencies for jvm - all modules default platforms`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--all-modules",
        )

        r.checkGold("jvm-exported-dependencies_all_default")
    }

    @Test
    fun `show dependencies for jvm module with --include-tests`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--include-tests",
        )

        r.checkGold("jvm-exported-dependencies_root_tests")
    }

    @Test
    fun `show dependencies for jvm module with --scope=compile`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--scope=compile",
        )

        r.checkGold("jvm-exported-dependencies_root_compile")
    }

    @Test
    fun `show dependencies for jvm module with --scope=runtime`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--scope=runtime",
        )

        r.checkGold("jvm-exported-dependencies_root_runtime")
    }

    @Test
    fun `show dependencies for jvm module with group common`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=common",
        )

        r.checkGold("jvm-exported-dependencies_root_common")
    }

    @Test
    fun `show dependencies for jvm module with inexistent platform`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=notaplatform",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains(
            """
            ERROR: Invalid platform group name 'notaplatform'.
            
            Supported platform groups for module 'root': common, jvm
        """.trimIndent()
        )
    }

    @Test
    fun `show dependencies for jvm module with platform typo (single choice)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-exported-dependencies"),
            "show", "dependencies", "--module", "root", "--platform-group=commno",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        r.assertStderrContains(
            """
            ERROR: Invalid platform group name 'commno'. Did you mean common?
            
            Supported platform groups for module 'root': common, jvm
        """.trimIndent()
        )
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

        r.checkGold("multiplatform-lib-with-alias_default")
    }

    @Test
    fun `show dependencies for multiplatform module with --include-tests`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--include-tests",
        )

        r.checkGold("multiplatform-lib-with-alias_tests")
    }

    @Test
    fun `show dependencies for multiplatform module with group common`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=common",
        )

        r.checkGold("multiplatform-lib-with-alias_common")
    }

    @Test
    fun `show dependencies for multiplatform module with group native`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=native",
        )

        r.checkGold("multiplatform-lib-with-alias_native")
    }

    @Test
    fun `show dependencies for multiplatform module with group jvm`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=jvm",
        )

        r.checkGold("multiplatform-lib-with-alias_jvm")
    }

    @Test
    fun `show dependencies for multiplatform module with group from alias`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-lib-with-alias"),
            "show", "dependencies", "--platform-group=jvmAndAndroid",
        )

        r.checkGold("multiplatform-lib-with-alias_alias_jvmAndAndroid")
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
