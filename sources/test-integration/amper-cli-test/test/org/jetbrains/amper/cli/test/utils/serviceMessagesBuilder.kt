/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.utils

import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import jetbrains.buildServer.messages.serviceMessages.TestFinished
import jetbrains.buildServer.messages.serviceMessages.TestIgnored
import jetbrains.buildServer.messages.serviceMessages.TestStarted
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted
import java.util.*

fun buildServiceMessages(block: ServiceMessagesBuilder.() -> Unit): List<ServiceMessage> =
    ServiceMessagesBuilder().apply(block).build()

class ServiceMessagesBuilder {

    private val messages = mutableListOf<ServiceMessage>()
    private var flowStack = ArrayDeque<Int>()
    private var nextFlowId = 0

    var currentFlowId: String? = null
        private set
    var currentTest: String? = null
        private set
    var currentTestDisplayName: String? = null
        private set
    var currentSuite: String? = null
        private set
    var currentSuiteDisplayName: String? = null
        private set

    fun build(): List<ServiceMessage> = messages.toList()

    fun flow(block: ServiceMessagesBuilder.() -> Unit) {
        val parentFlowId = flowStack.lastOrNull()
        val flowId = nextFlowId++
        val flowString = flowId.toString()
        currentFlowId = flowString
        flowStack.addLast(flowId)
        messages.add(FlowStarted(flowId = flowString, parent = parentFlowId?.toString()))
        block()
        messages.add(FlowFinished(flowId = flowString))
        flowStack.removeLast()
    }

    fun suite(
        name: String,
        displayName: String,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit = {},
    ) {
        currentSuite = name
        currentSuiteDisplayName = displayName
        val flowId = currentFlowId
        messages.add(
            TestSuiteStarted(name)
                .withFlowId(flowId)
                .withLocationHint(locationHint)
                .withDisplayName(displayName)
        )
        block()
        messages.add(TestSuiteFinished(name).withFlowId(flowId).withDisplayName(displayName))
        currentSuiteDisplayName = null
        currentSuite = null
    }

    fun suiteWithFlow(
        name: String,
        displayName: String = name,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit = {},
    ) {
        flow {
            suite(name, displayName, locationHint, block)
        }
    }

    fun test(
        name: String,
        displayName: String,
        captureStdOutput: Boolean = false,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit,
    ) {
        currentTest = name
        currentTestDisplayName = displayName
        val flowId = currentFlowId
        messages.add(TestStarted(name, captureStdOutput, locationHint).withFlowId(flowId).withDisplayName(displayName))
        block()
        messages.add(TestFinished(name, 42).withFlowId(flowId).withDisplayName(displayName))
        currentTestDisplayName = null
        currentTest = null
    }

    fun testWithFlow(
        name: String,
        displayName: String = name,
        captureStdOutput: Boolean = false,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit,
    ) {
        flow {
            test(name, displayName, captureStdOutput, locationHint, block)
        }
    }

    fun testStdOut(output: String, withTimestamp: Boolean = false) {
        val testName = currentTest ?: error("Not in a test")
        val testDisplayName = currentTestDisplayName ?: error("Test display name is not set")
        messages.add(
            TestStdOut(testName, output).withFlowId(currentFlowId).withDisplayName(testDisplayName)
                .apply { if (withTimestamp) withSomeTimestamp() }
        )
    }

    fun testStdErr(output: String, withTimestamp: Boolean = false) {
        val testName = currentTest ?: error("Not in a test")
        val testDisplayName = currentTestDisplayName ?: error("Test display name is not set")
        messages.add(
            TestStdErr(testName, output).withFlowId(currentFlowId).withDisplayName(testDisplayName)
                .apply { if (withTimestamp) withSomeTimestamp() }
        )
    }

    fun testFailed(message: String, serializedStackTrace: String) {
        val testName = currentTest ?: error("Not in a test")
        val testDisplayName = currentTestDisplayName ?: error("Test display name is not set")
        val sm = "##teamcity[testFailed name='$testName' message='$message' details='$serializedStackTrace']"
        messages.add(ServiceMessage.parse(sm)!!.withFlowId(currentFlowId).withDisplayName(testDisplayName))
    }

    fun testFailed(message: String, expectedValue: String, actualValue: String, serializedStackTrace: String) {
        val testName = currentTest ?: error("Not in a test")
        val testDisplayName = currentTestDisplayName ?: error("Test display name is not set")
        val sm =
            "##teamcity[testFailed name='$testName' message='$message' details='$serializedStackTrace' actual='$actualValue' expected='$expectedValue']"
        messages.add(
            ServiceMessage.parse(sm)!!.withFlowId(currentFlowId).withDisplayName(testDisplayName)
        )
    }

    fun testIgnored(message: String) {
        val testName = currentTest ?: error("Not in a test")
        val testDisplayName = currentTestDisplayName ?: error("Test display name is not set")
        messages.add(TestIgnored(testName, message).withFlowId(currentFlowId).withDisplayName(testDisplayName))
    }

    fun testSuiteIgnored(message: String) {
        val suiteName = currentSuite ?: error("Not in a suite")
        val suiteDisplayName = currentSuiteDisplayName ?: error("Suite display name is not set")
        messages.add(TestIgnored(suiteName, message).withFlowId(currentFlowId).withDisplayName(suiteDisplayName))
    }
}

private fun ServiceMessage.withFlowId(flow: String?): ServiceMessage {
    if (flow != null) {
        setFlowId(flow)
    }
    return this
}

private fun ServiceMessage.withSomeTimestamp(): ServiceMessage {
    setTimestamp(Date())
    return this
}

/**
 * Adds more user-friendly display name to the given [ServiceMessage].
 */
private fun ServiceMessage.withDisplayName(displayName: String): ServiceMessage = object : MessageWithAttributes(
    messageName,
    attributes + mapOf("displayName" to displayName)
) {}

/**
 * Adds a location hint to the given [ServiceMessage].
 */
private fun ServiceMessage.withLocationHint(locationHint: String?): ServiceMessage {
    if (locationHint == null) return this
    return object : MessageWithAttributes(messageName, attributes + mapOf("locationHint" to locationHint)) {}
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
