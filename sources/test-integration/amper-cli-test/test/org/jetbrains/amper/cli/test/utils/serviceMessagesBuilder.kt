/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.utils

import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import jetbrains.buildServer.messages.serviceMessages.TestFinished
import jetbrains.buildServer.messages.serviceMessages.TestStarted
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted
import java.util.*
import kotlin.collections.ArrayDeque

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

    fun build(): List<ServiceMessage> = messages.toList()

    fun flow(block: ServiceMessagesBuilder.() -> Unit) {
        val parentFlowId = flowStack.lastOrNull()
        val flowId = nextFlowId++
        currentFlowId = flowId.toString()
        flowStack.addLast(flowId)
        messages.add(FlowStarted(flowId = currentFlowId!!, parent = parentFlowId?.toString()))
        block()
        messages.add(FlowFinished(flowId = currentFlowId!!))
        flowStack.removeLast()
    }

    fun suite(name: String, block: ServiceMessagesBuilder.() -> Unit = {}) {
        messages.add(TestSuiteStarted(name).withFlowId(currentFlowId))
        block()
        messages.add(TestSuiteFinished(name).withFlowId(currentFlowId))
    }

    fun suiteWithFlow(name: String, block: ServiceMessagesBuilder.() -> Unit = {}) {
        flow {
            suite(name, block)
        }
    }

    fun test(
        name: String,
        captureStdOutput: Boolean = false,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit,
    ) {
        currentTest = name
        messages.add(TestStarted(name, captureStdOutput, locationHint).withFlowId(currentFlowId))
        block()
        messages.add(TestFinished(name, 42).withFlowId(currentFlowId))
        currentTest = null
    }

    fun testWithFlow(
        name: String,
        captureStdOutput: Boolean = false,
        locationHint: String? = null,
        block: ServiceMessagesBuilder.() -> Unit,
    ) {
        flow {
            test(name, captureStdOutput, locationHint, block)
        }
    }

    fun testStdOut(output: String, withTimestamp: Boolean = false) {
        val testName = currentTest ?: error("Not in a test")
        messages.add(TestStdOut(testName, output).withFlowId(currentFlowId).apply { if (withTimestamp) withSomeTimestamp() })
    }

    fun testStdErr(output: String, withTimestamp: Boolean = false) {
        val testName = currentTest ?: error("Not in a test")
        messages.add(TestStdErr(testName, output).withFlowId(currentFlowId).apply { if (withTimestamp) withSomeTimestamp() })
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
