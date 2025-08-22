/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.jetbrains.amper.cli.test.utils.assertStdoutContains
import org.jetbrains.amper.cli.test.utils.buildServiceMessages
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val NL = System.lineSeparator()

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestFormatTest : AmperCliTestBase() {

    @Test
    fun `pretty CLI output format should be used by default`() = runSlowTest {
        val r = runCli(
            projectRoot = testProject("jvm-failed-test"),
            "test",
            assertEmptyStdErr = false,
            expectedExitCode = 1,
        )

        val expectedFailureOutput = """
            Started doTest()
            Passed doTest()
            Started stringComparisonFailure()
            Failed stringComparisonFailure()
                       => Exception: org.opentest4j.AssertionFailedError: Strings are not equal ==> expected: <EXPECTED_VALUE> but was: <ACTUAL_VALUE>
                            at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
                            at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
                            at org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:197)
                            at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
                            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1156)
                            at kotlin.test.junit5.JUnit5Asserter.assertEquals(JUnitSupport.kt:32)
                            at kotlin.test.AssertionsKt__AssertionsKt.assertEquals(Assertions.kt:63)
                            at kotlin.test.AssertionsKt.assertEquals(Unknown Source)
                            at FailedTest.stringComparisonFailure(tests.kt:18)
                            at java.base/java.lang.reflect.Method.invoke(Method.java:580)
                            at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
                            at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
            Started booleanFailure()
            Failed booleanFailure()
                       => Exception: org.opentest4j.AssertionFailedError: The boolean value is incorrect
                            at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)
                            at org.junit.jupiter.api.Assertions.fail(Assertions.java:138)
                            at kotlin.test.junit5.JUnit5Asserter.fail(JUnitSupport.kt:56)
                            at kotlin.test.Asserter.assertTrue(Assertions.kt:694)
                            at kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
                            at kotlin.test.Asserter.assertTrue(Assertions.kt:704)
                            at kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
                            at kotlin.test.AssertionsKt__AssertionsKt.assertTrue(Assertions.kt:44)
                            at kotlin.test.AssertionsKt.assertTrue(Unknown Source)
                            at FailedTest.booleanFailure(tests.kt:13)
                            at java.base/java.lang.reflect.Method.invoke(Method.java:580)
                            at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
                            at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
                            """.trimIndent()

        val outputPartToCheck = r.stdout
            .substringAfter("Started FailedTest")
            .substringBefore("Completed FailedTest")
            .trim()
        assertEquals(expectedFailureOutput, outputPartToCheck, "Output is incorrect")
    }

    @Test
    fun `test failure should print expected and actual values in teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                projectRoot = testProject("jvm-failed-test"),
                "test",
                "--format=teamcity",
                assertEmptyStdErr = false,
                expectedExitCode = 1,
            )

            val serviceMessages = parseTeamCityServiceMessages(r.stdout)
            val expectedMessages = buildServiceMessages {
                suiteWithFlow("JUnit Platform Suite")
                suiteWithFlow("JUnit Jupiter") {
                    suiteWithFlow("FailedTest") {
                        testWithFlow("FailedTest.doTest()") {
                        }
                        testWithFlow("FailedTest.stringComparisonFailure()") {
                            testFailed(
                                message = "Strings are not equal ==> expected: <EXPECTED_VALUE> but was: <ACTUAL_VALUE>",
                                expectedValue = "EXPECTED_VALUE",
                                actualValue = "ACTUAL_VALUE",
                                stackTrace = arrayOf(
                                    StackTraceElement("org.junit.jupiter.api.AssertionFailureBuilder", "build", "AssertionFailureBuilder.java", 151),
                                    StackTraceElement("org.junit.jupiter.api.AssertionFailureBuilder", "buildAndThrow", "AssertionFailureBuilder.java", 132),
                                    StackTraceElement("org.junit.jupiter.api.AssertEquals", "failNotEqual", "AssertEquals.java", 197),
                                    StackTraceElement("org.junit.jupiter.api.AssertEquals", "assertEquals", "AssertEquals.java", 182),
                                    StackTraceElement("org.junit.jupiter.api.Assertions", "assertEquals", "Assertions.java", 1156),
                                    StackTraceElement("kotlin.test.junit5.JUnit5Asserter", "assertEquals", "JUnitSupport.kt", 32),
                                    StackTraceElement("kotlin.test.AssertionsKt__AssertionsKt", "assertEquals", "Assertions.kt", 63),
                                    StackTraceElement("kotlin.test.AssertionsKt", "assertEquals", null, -1),
                                    StackTraceElement("FailedTest", "stringComparisonFailure", "tests.kt", 18),
                                    StackTraceElement("java.base/java.lang.reflect.Method", "invoke", "Method.java", 580),
                                    StackTraceElement("java.base/java.util.ArrayList", "forEach", "ArrayList.java", 1596),
                                    StackTraceElement("java.base/java.util.ArrayList", "forEach", "ArrayList.java", 1596),
                                )
                            )
                        }
                        testWithFlow("FailedTest.booleanFailure()") {
                            testFailed(
                                message = "The boolean value is incorrect",
                                stackTrace = arrayOf(
                                    StackTraceElement("org.junit.jupiter.api.AssertionUtils", "fail", "AssertionUtils.java", 38),
                                    StackTraceElement("org.junit.jupiter.api.Assertions", "fail", "Assertions.java", 138),
                                    StackTraceElement("kotlin.test.junit5.JUnit5Asserter", "fail", "JUnitSupport.kt", 56),
                                    StackTraceElement("kotlin.test.Asserter", "assertTrue", "Assertions.kt", 694),
                                    StackTraceElement("kotlin.test.junit5.JUnit5Asserter", "assertTrue", "JUnitSupport.kt", 30),
                                    StackTraceElement("kotlin.test.Asserter", "assertTrue", "Assertions.kt", 704),
                                    StackTraceElement("kotlin.test.junit5.JUnit5Asserter", "assertTrue", "JUnitSupport.kt", 30),
                                    StackTraceElement("kotlin.test.AssertionsKt__AssertionsKt", "assertTrue", "Assertions.kt", 44),
                                    StackTraceElement("kotlin.test.AssertionsKt", "assertTrue", null, -1),
                                    StackTraceElement("FailedTest", "booleanFailure", "tests.kt", 13),
                                    StackTraceElement("java.base/java.lang.reflect.Method", "invoke", "Method.java", 580),
                                    StackTraceElement("java.base/java.util.ArrayList", "forEach", "ArrayList.java", 1596),
                                    StackTraceElement("java.base/java.util.ArrayList", "forEach", "ArrayList.java", 1596),
                                )
                            )
                        }
                    }
                }
                suiteWithFlow("JUnit Vintage")
            }
            assertServiceMessagesEqual(expectedMessages, serviceMessages)
        }
    }

    @Test
    fun `junit 4 tests should print teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                projectRoot = testProject("multiplatform-tests"),
                "test", "-m", "jvm-cli", "--format=teamcity", "--include-classes=com.example.jvmcli.OrderedTestSuite",
                assertEmptyStdErr = false,
            )

            val serviceMessages = parseTeamCityServiceMessages(r.stdout)
            val expectedMessages = buildServiceMessages {
                suiteWithFlow("JUnit Platform Suite")
                suiteWithFlow("JUnit Jupiter")
                suiteWithFlow("JUnit Vintage") {
                    suiteWithFlow("com.example.jvmcli.OrderedTestSuite") {
                        suiteWithFlow("com.example.jvmcli.JvmIntegrationTest") {
                            testWithFlow("com.example.jvmcli.JvmIntegrationTest.integrationTest()") {
                                testStdOut("output line 1 in JvmIntegrationTest.integrationTest")
                                testStdOut(NL)
                                testStdErr("error line 1 in JvmIntegrationTest.integrationTest")
                                testStdErr(NL)
                                testStdOut("output line 2 in JvmIntegrationTest.integrationTest")
                                testStdOut(NL)
                                testStdErr("error line 2 in JvmIntegrationTest.integrationTest")
                                testStdErr(NL)
                            }
                        }
                        suiteWithFlow("com.example.jvmcli.MyClass1Test") {
                            testWithFlow("com.example.jvmcli.MyClass1Test.test1()") {
                                testStdOut("running MyClass1Test.test1")
                                testStdOut(NL)
                            }
                            testWithFlow("com.example.jvmcli.MyClass1Test.test2()") {
                                testStdOut("running MyClass1Test.test2")
                                testStdOut(NL)
                            }
                            testWithFlow("com.example.jvmcli.MyClass1Test.test3()") {
                                testStdOut("running MyClass1Test.test3")
                                testStdOut(NL)
                            }
                        }
                        suiteWithFlow("com.example.jvmcli.MyClass2Test") {
                            testWithFlow("com.example.jvmcli.MyClass2Test.test1()") {
                                testStdOut("running MyClass2Test.test1")
                                testStdOut(NL)
                            }
                            testWithFlow("com.example.jvmcli.MyClass2Test.test2()") {
                                testStdOut("running MyClass2Test.test2")
                                testStdOut(NL)
                            }
                            testWithFlow("com.example.jvmcli.MyClass2Test.test3()") {
                                testStdOut("running MyClass2Test.test3")
                                testStdOut(NL)
                            }
                        }
                    }
                }
            }
            assertServiceMessagesEqual(expectedMessages, serviceMessages)
        }
    }

    @Test
    fun `output should be printed only once in TC format`() {
        runSlowTest {
            val r = runCli(
                projectRoot = testProject("multiplatform-tests"),
                "test", "-m", "jvm-cli", "--format=teamcity", "--include-classes=com.example.jvmcli.MyClass1Test",
                assertEmptyStdErr = false,
            )
            r.assertStdoutContains("running MyClass1Test.test1", expectedOccurrences = 1)
        }
    }

    @Test
    fun `junit 5 tests with params should print teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                projectRoot = testProject("jvm-tests-with-params"),
                "test", "--format=teamcity",
                assertEmptyStdErr = false,
            )

            val serviceMessages = parseTeamCityServiceMessages(r.stdout)
            val expectedMessages = buildServiceMessages {
                suiteWithFlow("JUnit Platform Suite")
                suiteWithFlow("JUnit Jupiter") {
                    suiteWithFlow("com.example.testswithparams.OverloadsTest") {
                        testWithFlow("com.example.testswithparams.OverloadsTest.test()") {
                            testStdOut("running OverloadsTest.test()")
                            testStdOut(NL)
                        }
                        testWithFlow("com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo)") {
                            testStdOut("running OverloadsTest.test(TestInfo)")
                            testStdOut(NL)
                        }
                        testWithFlow("com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo, org.junit.jupiter.api.TestReporter)") {
                            testStdOut("running OverloadsTest.test(TestInfo, TestReporter)")
                            testStdOut(NL)
                        }
                    }
                }
                suiteWithFlow("JUnit Vintage")
            }
            assertServiceMessagesEqual(expectedMessages, serviceMessages)
        }
    }
}

private fun parseTeamCityServiceMessages(text: String): List<ServiceMessage> = text.lines()
    .filter { it.startsWith("##teamcity[") }
    .mapNotNull { ServiceMessage.parse(it) }

private fun assertServiceMessagesEqual(expected: List<ServiceMessage>, actual: List<ServiceMessage>) {
    for ((exp, act) in expected.zip(actual)) {
        assertEquals(exp.normalized(), act.normalized(), "Service messages don't match")
    }
    if (expected.size < actual.size) {
        fail("Unexpected extra service messages:\n${actual.drop(expected.size).joinToString("\n")}")
    }
    if (expected.size > actual.size) {
        fail("Missing service messages, expected ${expected.size - actual.size} more:\n${expected.drop(actual.size).joinToString("\n")}")
    }
}

private fun ServiceMessage.normalized(): String {
    val serializedMessage = asString()

    // The value of the flow IDs doesn't matter, what matters is that the links between different flows are preserved.
    // Therefore, we can replace all flow IDs in a reproducible way to normalize the messages.
    val normalizedFlowIds = mutableMapOf<String, Int>()
    var currentFlowId = 0
    val flowIdRegex = Regex("flowId='([^']+)'")
    val parentRegex = Regex("parent='([^']+)'")
    val messageWithFlows = flowIdRegex.findAll(serializedMessage).fold(serializedMessage) { msg, match ->
        val flowId = match.groupValues[1]
        val normalized = normalizedFlowIds.computeIfAbsent(flowId) { currentFlowId++ }
        msg
            .replace(flowIdRegex, "flowId='<normalized:$normalized>'")
            .replace(parentRegex, "parent='<normalized:$normalized>'")
    }
    return messageWithFlows
        .replace(Regex("timestamp='[^']+'"), "timestamp='<normalized>'")
        .replace(Regex("duration='[^']+'"), "duration='<normalized>'")
}
