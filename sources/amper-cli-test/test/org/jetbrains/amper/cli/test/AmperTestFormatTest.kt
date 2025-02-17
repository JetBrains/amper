/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import org.jetbrains.amper.cli.test.teamcity.buildServiceMessages
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val NL = System.lineSeparator()

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestFormatTest : AmperCliTestBase() {

    override val testDataRoot: Path = Dirs.amperTestProjectsRoot

    @Test
    fun `junit 4 tests should print teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                backendTestProjectName = "multiplatform-tests",
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
                                testStdOut("output line 1 in JvmIntegrationTest.integrationTest${NL}output line 2 in JvmIntegrationTest.integrationTest$NL")
                                testStdErr("error line 1 in JvmIntegrationTest.integrationTest${NL}error line 2 in JvmIntegrationTest.integrationTest$NL")
                            }
                        }
                        suiteWithFlow("com.example.jvmcli.MyClass1Test") {
                            testWithFlow("com.example.jvmcli.MyClass1Test.test1()") {
                                testStdOut("running MyClass1Test.test1$NL")
                            }
                            testWithFlow("com.example.jvmcli.MyClass1Test.test2()") {
                                testStdOut("running MyClass1Test.test2$NL")
                            }
                            testWithFlow("com.example.jvmcli.MyClass1Test.test3()") {
                                testStdOut("running MyClass1Test.test3$NL")
                            }
                        }
                        suiteWithFlow("com.example.jvmcli.MyClass2Test") {
                            testWithFlow("com.example.jvmcli.MyClass2Test.test1()") {
                                testStdOut("running MyClass2Test.test1$NL")
                            }
                            testWithFlow("com.example.jvmcli.MyClass2Test.test2()") {
                                testStdOut("running MyClass2Test.test2$NL")
                            }
                            testWithFlow("com.example.jvmcli.MyClass2Test.test3()") {
                                testStdOut("running MyClass2Test.test3$NL")
                            }
                        }
                    }
                }
            }
            assertServiceMessagesEqual(expectedMessages, serviceMessages)
        }
    }

    @Test
    fun `junit 5 tests with params should print teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                backendTestProjectName = "jvm-tests-with-params",
                "test", "--format=teamcity",
                assertEmptyStdErr = false,
            )

            val serviceMessages = parseTeamCityServiceMessages(r.stdout)
            val expectedMessages = buildServiceMessages {
                suiteWithFlow("JUnit Platform Suite")
                suiteWithFlow("JUnit Jupiter") {
                    suiteWithFlow("com.example.testswithparams.OverloadsTest") {
                        testWithFlow("com.example.testswithparams.OverloadsTest.test()") {
                            testStdOut("running OverloadsTest.test()$NL")
                        }
                        testWithFlow("com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo)") {
                            testStdOut("running OverloadsTest.test(TestInfo)$NL")
                        }
                        testWithFlow("com.example.testswithparams.OverloadsTest.test(org.junit.jupiter.api.TestInfo, org.junit.jupiter.api.TestReporter)") {
                            testStdOut("running OverloadsTest.test(TestInfo, TestReporter)$NL")
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
