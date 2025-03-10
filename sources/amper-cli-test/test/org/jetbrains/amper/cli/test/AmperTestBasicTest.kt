/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.WindowsOnly
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.event.Level
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestBasicTest : AmperCliTestBase() {

    @Test
    fun `jvm-kotlin-test-smoke test`() = runSlowTest {
        val projectRoot = testProject("jvm-kotlin-test-smoke")
        val result = runCli(projectRoot = projectRoot, "test")

        // not captured by default...
        result.assertStdoutContains("Hello from test method, JavaString")
        result.assertStdoutContains("[         1 tests successful      ]")
        result.assertStdoutContains("[         0 tests failed          ]")

        val xmlReport = result.buildOutputRoot.resolve("reports/jvm-kotlin-test-smoke/jvm/TEST-junit-vintage.xml")
            .readText()
        assertContains(xmlReport, "<testcase name=\"smoke\" classname=\"apkg.ATest\"")
    }

    @Test
    fun `jvm-failed-test`() = runSlowTest {
        val projectRoot = testProject("jvm-failed-test")
        val result = runCli(projectRoot = projectRoot, "test", assertEmptyStdErr = false, expectedExitCode = 1)
        result.assertStderrContains("ERROR: JVM tests failed for module 'jvm-failed-test' with exit code 1 (see errors above)")
        result.assertStdoutContains("MethodSource [className = 'FailedTest', methodName = 'shouldFail', methodParameterTypes = '']")
        result.assertStdoutContains("=> java.lang.AssertionError: Expected value to be true.")
    }

    @Test
    fun `fails when no tests were discovered`() = runSlowTest {
        // Testing a module should fail if there are some test sources, but no tests were found
        // for example it'll automatically fail if you run your tests with TestNG, but specified JUnit in settings
        // see `native test no tests`
        val result = runCli(
            projectRoot = testProject("jvm-kotlin-test-no-tests"),
            "test",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.assertStderrContains("JVM tests failed for module 'jvm-kotlin-test-no-tests' with exit code 2 (no tests were discovered) (see errors above)")
    }

    @Test
    fun `run tests only from test fragment`() = runSlowTest {
        val projectContext = testProject("jvm-test-classpath")
        val result = runCli(projectRoot = projectContext, "test")

        // asserts that ATest.smoke is run, but SrcTest.smoke isn't
        result.assertStdoutContains("[         1 tests successful      ]")
        result.assertStdoutContains("[         0 tests failed          ]")

        val xmlReport = result.buildOutputRoot.resolve("reports/jvm-test-classpath/jvm/TEST-junit-jupiter.xml")
            .readText()
        assertContains(xmlReport, "<testcase name=\"smoke()\" classname=\"apkg.ATest\"")
    }

    @Test
    fun `test fragment dependencies`() = runSlowTest {
        val result = runCli(projectRoot = testProject("jvm-test-fragment-dependencies"), "test")
        result.assertStdoutContains("FromExternalDependencies:OneTwo FromProject:MyUtil")
    }

    // this test is useful in case we change our JUnit runner for a custom one using Kotlin (causing classpath issues)
    @Test
    fun `test using reflection`() = runSlowTest {
        runCli(testProject("jvm-test-using-reflection"), "test", "--platform=jvm")
    }

    @Test
    fun `jvm test with JVM arg`() = runSlowTest {
        val testProject = testProject("jvm-kotlin-test-systemprop")
        runCli(testProject, "test", "--jvm-args=-Dmy.system.prop=hello")

        // should fail without the system prop
        runCli(
            projectRoot = testProject,
            "test",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )

        // should fail with an incorrect value for the system prop
        runCli(
            projectRoot = testProject,
            "test",
            "--jvm-args=-Dmy.system.prop=WRONG",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
    }

    @Test
    fun `jvm test custom engine`() = runSlowTest {
        val result = runCli(projectRoot = testProject("jvm-test-custom-engine"), "test")

        // tests are discovered
        result.assertStdoutContains("my-test-1")
        result.assertStdoutContains("my-test-2")
    }

    @Test
    @MacOnly
    fun `missing platform to test`() = runSlowTest {
        val projectContext = testProject("jvm-kotlin-test-no-tests")
        val result = runCli(
            projectRoot = projectContext,
            "test", "--platform=iosSimulatorArm64",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.assertStderrContains("No test tasks were found for platforms: IOS_SIMULATOR_ARM64")
    }

    @Test
    @MacOnly
    fun `unsupported platform to test`() = runSlowTest {
        val projectContext = testProject("simple-multiplatform-cli")
        val result = runCli(
            projectRoot = projectContext,
            "test", "--platform=mingwX64",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.assertStderrContains("""
            Unable to run requested platform(s) on the current system.

            Requested unsupported platforms: mingwX64
            Runnable platforms on the current system: android iosSimulatorArm64 jvm macosArm64 tvosSimulatorArm64 watchosSimulatorArm64
        """.trimIndent())
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-474")
    fun `native test no tests`() = runSlowTest {
        // Testing a module should fail if there are some test sources, but no tests were found

        val projectContext = testProject("native-test-no-tests")
        val result = runCli(
            projectRoot = projectContext,
            "test",
            expectedExitCode = 1,
            assertEmptyStdErr = false,
        )
        result.assertStderrContains("Some message about `no tests were discovered`")
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-475")
    fun `native test app test`() = runSlowTest {
        val result = runCli(projectRoot = testProject("native-test-app-test"), "test")
        // TODO assert that some test was actually run
    }

    @Test
    fun `should warn on no test sources (jvm)`() = runSlowTest {
        // Testing a module should not fail if there are no test sources at all but warn about it
        val result = runCli(projectRoot = testProject("jvm-kotlin-test-no-test-sources"), "test")
        result.assertLogStartsWith("No test classes, skipping test execution for module 'jvm-kotlin-test-no-test-sources'", Level.WARN)
    }

    @Test
    @WindowsOnly
    @Ignore("AMPER-476")
    fun `should warn on no test sources (native)`() = runSlowTest {
        // Testing a module should not fail if there are no test sources at all but warn about it
        val result = runCli(projectRoot = testProject("native-test-no-test-sources"), "test", "--platform=mingwX64")
        result.assertLogStartsWith("No test classes, skipping test execution for module 'native-test-no-test-sources'", Level.WARN)
    }
}
