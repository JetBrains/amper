/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import jetbrains.buildServer.messages.serviceMessages.TestFinished
import jetbrains.buildServer.messages.serviceMessages.TestStarted
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private val NL = System.lineSeparator()

// CONCURRENT is here to test that multiple concurrent amper processes work correctly.
@Execution(ExecutionMode.CONCURRENT)
class AmperTestFormatTest : AmperCliTestBase() {

    override val testDataRoot: Path = Dirs.amperTestProjectsRoot

    @Test
    fun `junit tests should print teamcity service messages`() {
        runSlowTest {
            val r = runCli(
                backendTestProjectName = "multiplatform-tests",
                "test", "-m", "jvm-cli", "--format=teamcity",
                assertEmptyStdErr = false,
            )

            val serviceMessages = parseTeamCityServiceMessages(r.stdout)
            val expectedMessages = listOf(
                TestSuiteStarted("JUnit Platform Suite").withFlowId(0),
                FlowStarted(flowId = "-1", parent = null),
                FlowFinished(flowId = "-1"),
                TestSuiteFinished("JUnit Platform Suite").withFlowId(0),

                TestSuiteStarted("JUnit Jupiter").withFlowId(0),
                FlowStarted(flowId = "0", parent = null),
                FlowFinished(flowId = "0"),
                TestSuiteFinished("JUnit Jupiter").withFlowId(0),

                TestSuiteStarted("JUnit Vintage").withFlowId(1),
                FlowStarted(flowId = "1", parent = null),

                TestSuiteStarted("com.example.jvmcli.JvmIntegrationTest").withFlowId(2),
                FlowStarted(flowId = "2", parent = "1"),
                TestStarted("com.example.jvmcli.JvmIntegrationTest.integrationTest()", false, null).withFlowId(3),
                FlowStarted(flowId = "3", parent = "2"),
                TestStdOut("com.example.jvmcli.JvmIntegrationTest.integrationTest()", "output line 1 in JvmIntegrationTest.integrationTest${NL}output line 2 in JvmIntegrationTest.integrationTest$NL").withFlowId(3).withSomeTimestamp(),
                TestStdErr("com.example.jvmcli.JvmIntegrationTest.integrationTest()", "error line 1 in JvmIntegrationTest.integrationTest${NL}error line 2 in JvmIntegrationTest.integrationTest$NL").withFlowId(3).withSomeTimestamp(),
                FlowFinished(flowId = "3"),
                TestFinished("com.example.jvmcli.JvmIntegrationTest.integrationTest()", 42).withFlowId(3),
                FlowFinished(flowId = "2"),
                TestSuiteFinished("com.example.jvmcli.JvmIntegrationTest").withFlowId(2),

                TestSuiteStarted("com.example.jvmcli.MyClass1Test").withFlowId(4),
                FlowStarted(flowId = "4", parent = "1"),
                TestStarted("com.example.jvmcli.MyClass1Test.test1()", false, null).withFlowId(5),
                FlowStarted(flowId = "5", parent = "4"),
                TestStdOut("com.example.jvmcli.MyClass1Test.test1()", "running MyClass1Test.test1$NL").withFlowId(5).withSomeTimestamp(),
                FlowFinished(flowId = "5"),
                TestFinished("com.example.jvmcli.MyClass1Test.test1()", 42).withFlowId(5),
                TestStarted("com.example.jvmcli.MyClass1Test.test2()", false, null).withFlowId(6),
                FlowStarted(flowId = "6", parent = "4"),
                TestStdOut("com.example.jvmcli.MyClass1Test.test2()", "running MyClass1Test.test2$NL").withFlowId(6).withSomeTimestamp(),
                FlowFinished(flowId = "6"),
                TestFinished("com.example.jvmcli.MyClass1Test.test2()", 42).withFlowId(6),
                TestStarted("com.example.jvmcli.MyClass1Test.test3()", false, null).withFlowId(7),
                FlowStarted(flowId = "7", parent = "4"),
                TestStdOut("com.example.jvmcli.MyClass1Test.test3()", "running MyClass1Test.test3$NL").withFlowId(7).withSomeTimestamp(),
                FlowFinished(flowId = "7"),
                TestFinished("com.example.jvmcli.MyClass1Test.test3()", 42).withFlowId(7),
                FlowFinished(flowId = "4"),
                TestSuiteFinished("com.example.jvmcli.MyClass1Test").withFlowId(4),

                TestSuiteStarted("com.example.jvmcli.MyClass2Test").withFlowId(8),
                FlowStarted(flowId = "8", parent = "1"),
                TestStarted("com.example.jvmcli.MyClass2Test.test1()", false, null).withFlowId(9),
                FlowStarted(flowId = "9", parent = "8"),
                TestStdOut("com.example.jvmcli.MyClass2Test.test1()", "running MyClass2Test.test1$NL").withFlowId(9).withSomeTimestamp(),
                FlowFinished(flowId = "9"),
                TestFinished("com.example.jvmcli.MyClass2Test.test1()", 42).withFlowId(9),
                TestStarted("com.example.jvmcli.MyClass2Test.test2()", false, null).withFlowId(10),
                FlowStarted(flowId = "10", parent = "8"),
                TestStdOut("com.example.jvmcli.MyClass2Test.test2()", "running MyClass2Test.test2$NL").withFlowId(10).withSomeTimestamp(),
                FlowFinished(flowId = "10"),
                TestFinished("com.example.jvmcli.MyClass2Test.test2()", 42).withFlowId(10),
                TestStarted("com.example.jvmcli.MyClass2Test.test3()", false, null).withFlowId(11),
                FlowStarted(flowId = "11", parent = "8"),
                TestStdOut("com.example.jvmcli.MyClass2Test.test3()", "running MyClass2Test.test3$NL").withFlowId(11).withSomeTimestamp(),
                FlowFinished(flowId = "11"),
                TestFinished("com.example.jvmcli.MyClass2Test.test3()", 42).withFlowId(11),
                FlowFinished(flowId = "8"),
                TestSuiteFinished("com.example.jvmcli.MyClass2Test").withFlowId(8),

                FlowFinished(flowId = "1"),
                TestSuiteFinished("JUnit Vintage").withFlowId(1),
            )

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

private fun ServiceMessage.withFlowId(n: Int): ServiceMessage {
    setFlowId(n.toString())
    return this
}

private fun ServiceMessage.withSomeTimestamp(): ServiceMessage {
    setTimestamp(Date())
    return this
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

/**
 * Represents a "flow started" message as defined in
 * [the TeamCity docs](https://www.jetbrains.com/help/teamcity/2024.12/service-messages.html#Message+FlowId).
 */
private class FlowStarted(flowId: String, parent: String? = null) : MessageWithAttributes(
    ServiceMessageTypes.FLOW_STARTED,
    buildMap {
        put("flowId", flowId)
        if (parent != null) {
            put("parent", parent)
        }
    },
)

/**
 * Represents a "flow finished" message as defined in
 * [the TeamCity docs](https://www.jetbrains.com/help/teamcity/2024.12/service-messages.html#Message+FlowId).
 */
private class FlowFinished(flowId: String) : MessageWithAttributes(
    ServiceMessageTypes.FLOW_FINSIHED,
    buildMap {
        put("flowId", flowId)
    },
)
