/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestFiltersTest : AmperCliTestBase() {

    @Test
    fun `include test single (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-test=com.example.jvmcli.MyClass1Test.test1",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
    }

    @Test
    fun `include test single with parentheses (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-test=com.example.jvmcli.MyClass1Test.test1()",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
    }

    @Test
    fun `include test single (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-test=com.example.shared.WorldTest.doTest",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running WorldTest.doTest", nOccurrences = 2) // jvm + current platform
    }

    @Test
    fun `include test single with parentheses (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-test=com.example.shared.WorldTest.doTest()",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running WorldTest.doTest", nOccurrences = 2) // jvm + current platform
    }

    @Test
    fun `include test single with 0 params (jvm-tests-with-params)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-tests-with-params"),
            "test",
            "--include-test=com.example.testswithparams.OverloadsTest.test()",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running OverloadsTest.test()")
    }

    @Test
    fun `include test single with 1 param (jvm-tests-with-params)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-tests-with-params"),
            "test",
            "--include-test=com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo)",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running OverloadsTest.test(TestInfo)")
    }

    @Test
    fun `include test single with 2 params (jvm-tests-with-params)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-tests-with-params"),
            "test",
            "--include-test=com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo,org.junit.jupiter.api.TestReporter)",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running OverloadsTest.test(TestInfo, TestReporter)")
    }

    @Test
    fun `run all parameterized tests (jvm-tests-with-params)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-tests-with-params"),
            "test",
        )
        r.assertJUnitTestCount(expected = 3)
        r.assertStdoutContainsLine("running OverloadsTest.test()")
        r.assertStdoutContainsLine("running OverloadsTest.test(TestInfo)")
        r.assertStdoutContainsLine("running OverloadsTest.test(TestInfo, TestReporter)")
    }

    @Test
    fun `include test multiple (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-test=com.example.jvmcli.MyClass1Test.test1",
            "--include-test=com.example.jvmcli.MyClass1Test.test2",
        )
        r.assertJUnitTestCount(expected = 2)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
    }

    @Test
    fun `include class exact single (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-classes=com.example.jvmcli.MyClass1Test",
        )
        r.assertJUnitTestCount(expected = 3)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass1Test.test3")
    }

    @Test
    fun `include class exact single (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-classes=com.example.shared.WorldTest",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running WorldTest.doTest", nOccurrences = 2) // jvm + current platform
    }

    @Test
    fun `include class exact multiple (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-classes=com.example.jvmcli.MyClass1Test",
            "--include-classes=com.example.jvmcli.MyClass2Test",
        )
        r.assertJUnitTestCount(expected = 6)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass1Test.test3")
        r.assertStdoutContainsLine("running MyClass2Test.test1")
        r.assertStdoutContainsLine("running MyClass2Test.test2")
        r.assertStdoutContainsLine("running MyClass2Test.test3")
    }

    @Test
    fun `include exact test and include exact class (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-test=com.example.jvmcli.MyClass1Test.test2",
            "--include-classes=com.example.jvmcli.MyClass2Test",
        )
        r.assertJUnitTestCount(expected = 4)
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass2Test.test1")
        r.assertStdoutContainsLine("running MyClass2Test.test2")
        r.assertStdoutContainsLine("running MyClass2Test.test3")
    }

    @Test
    fun `include class pattern (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-classes=com.example.jvmcli.MyClass*Test",
        )
        r.assertJUnitTestCount(expected = 6)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass1Test.test3")
        r.assertStdoutContainsLine("running MyClass2Test.test1")
        r.assertStdoutContainsLine("running MyClass2Test.test2")
        r.assertStdoutContainsLine("running MyClass2Test.test3")
    }

    @Test
    fun `include class pattern (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-classes=com.example.shared.World*",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running WorldTest.doTest", nOccurrences = 2) // jvm + current platform
    }

    // FIXME this should work. How to make JUnit accept it? It seems it doesn't consider "all tests" by default
    @Test
    fun `exclude class exact single (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--exclude-classes=com.example.jvmcli.MyClass2Test",
            assertEmptyStdErr = false, // some tests print to stderr
        )
        r.assertJUnitTestCount(expected = 4)
        r.assertStdoutContainsLine("output line 1 in JvmIntegrationTest.integrationTest")
        r.assertStdoutContainsLine("output line 2 in JvmIntegrationTest.integrationTest")
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass1Test.test3")
        assertEquals(listOf(
            "error line 1 in JvmIntegrationTest.integrationTest",
            "error line 2 in JvmIntegrationTest.integrationTest",
        ), r.stderr.trim().lines())
    }

    @Test
    fun `include pattern and exclude exact (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-classes=com.example.jvmcli.MyClass*Test",
            "--exclude-classes=com.example.jvmcli.MyClass2Test",
        )
        r.assertJUnitTestCount(expected = 3)
        r.assertStdoutContainsLine("running MyClass1Test.test1")
        r.assertStdoutContainsLine("running MyClass1Test.test2")
        r.assertStdoutContainsLine("running MyClass1Test.test3")
    }

    @Test
    fun `include pattern and exclude exact (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-classes=com.example.shared.*",
            "--exclude-classes=com.example.shared.SharedIntegrationTest",
        )
        r.assertJUnitTestCount(expected = 4)
        r.assertNativeTestCount(expected = 4)
        r.assertStdoutContainsLine("running WorldTest.doTest", nOccurrences = 2) // jvm + current platform
        r.assertStdoutContainsLine("running EnclosingClass.enclosingClassTest", nOccurrences = 2) // jvm + current platform
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass1.myNestedTest", nOccurrences = 2) // jvm + current platform
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass2.myNestedTest", nOccurrences = 2) // jvm + current platform
    }

    @Disabled // not supported by JUnit Console Launcher
    @Test
    fun `include exact and exclude class (jvm-cli)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "--include-test=com.example.jvmcli.MyClass1Test.test2",
            "--exclude-classes=com.example.jvmcli.MyClass1Test",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertStdoutContainsLine("running MyClass1Test.test2")
    }

    @Test
    fun `include pattern across multiple modules`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "jvm-cli",
            "-m",
            "shared",
            "--include-classes=*IntegrationTest",
            assertEmptyStdErr = false, // some tests print to stderr
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("output line 1 in JvmIntegrationTest.integrationTest")
        r.assertStdoutContainsLine("output line 2 in JvmIntegrationTest.integrationTest")
        r.assertStdoutContainsLine("running SharedIntegrationTest.integrationTest", nOccurrences = 2)
        assertEquals(listOf(
            "error line 1 in JvmIntegrationTest.integrationTest",
            "error line 2 in JvmIntegrationTest.integrationTest",
        ), r.stderr.trim().lines())
    }

    @Test
    fun `include exact nested class (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-classes=com.example.shared.EnclosingClass/NestedClass1",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass1.myNestedTest", nOccurrences = 2) // jvm + current platform
    }

    @Test
    fun `include nested class pattern (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-classes=com.example.shared.EnclosingClass/NestedClass*",
        )
        r.assertJUnitTestCount(expected = 2)
        r.assertNativeTestCount(expected = 2)
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass1.myNestedTest", nOccurrences = 2) // jvm + current platform
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass2.myNestedTest", nOccurrences = 2) // jvm + current platform
    }

    @Test
    fun `include exact nested class method (shared)`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("multiplatform-tests"),
            "test",
            "-m",
            "shared",
            "--include-test=com.example.shared.EnclosingClass/NestedClass1.myNestedTest",
        )
        r.assertJUnitTestCount(expected = 1)
        r.assertNativeTestCount(expected = 1)
        r.assertStdoutContainsLine("running EnclosingClass.NestedClass1.myNestedTest", nOccurrences = 2) // jvm + current platform
    }

    private val junitTestCountRegex = Regex("""\[\s*(?<count>\d+) tests found\s*]""")

    private fun AmperCliResult.assertJUnitTestCount(expected: Int) {
        val countMatch = stdout.lines().firstNotNullOfOrNull { junitTestCountRegex.matchEntire(it.trim()) }
            ?: fail("JUnit test count not present in stdout: $stdout")
        val count = countMatch.groups["count"]?.value?.toIntOrNull()
            ?: fail("JUnit test count could not be parsed: ${countMatch.groups}")
        assertEquals(expected, count, "Expected $expected 'found' JVM tests but got $count")
    }

    private val nativeTestCountRegex = Regex("""\[==========] (?<count>\d+) tests from \d+ test cases ran.*""")

    private fun AmperCliResult.assertNativeTestCount(expected: Int) {
        val countMatch = stdout.lines().firstNotNullOfOrNull { nativeTestCountRegex.matchEntire(it.trim()) }
            ?: fail("Native test count not present in stdout: $stdout")
        val count = countMatch.groups["count"]?.value?.toIntOrNull()
            ?: fail("Native test count could not be parsed: ${countMatch.groups}")
        assertEquals(expected, count, "Expected $expected 'found' native tests but got $count")
    }
}
