/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.listeners

import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import jetbrains.buildServer.messages.serviceMessages.TestFailed
import jetbrains.buildServer.messages.serviceMessages.TestFinished
import jetbrains.buildServer.messages.serviceMessages.TestIgnored
import jetbrains.buildServer.messages.serviceMessages.TestStarted
import jetbrains.buildServer.messages.serviceMessages.TestStdErr
import jetbrains.buildServer.messages.serviceMessages.TestStdOut
import jetbrains.buildServer.messages.serviceMessages.TestSuiteFinished
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val configPropertyGroup = "org.jetbrains.amper.junit.listener.teamcity"

@Suppress("unused") // used by ServiceLoader
class TeamCityMessagesTestExecutionListener(
    // We need to select and store the stream early (even System.out) to prevent JUnit
    // from capturing our service messages as test output.
    private val serviceMessagesStream: PrintStream = System.out, // default used when loaded via ServiceLoader
) : TestExecutionListener {

    private val enabled = System.getProperty("$configPropertyGroup.enabled", "false").toBooleanStrict()

    private val startTimes = mutableMapOf<UniqueId, TimeMark>()

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (!enabled) return
        when (testIdentifier.type) {
            TestDescriptor.Type.CONTAINER -> {
                emit(TestSuiteStarted(testIdentifier.teamCityName).withFlowId(testIdentifier))
            }
            TestDescriptor.Type.TEST,
            TestDescriptor.Type.CONTAINER_AND_TEST -> {
                markStart(testIdentifier)
                // disable automatic stdout/stderr capture between start&stop messages
                // (we use proper service messages to report it, so no need for guesswork)
                val captureStdOutput = false
                val locationHint = testIdentifier.toTeamCityLocationHint()
                emit(TestStarted(testIdentifier.teamCityName, captureStdOutput, locationHint).withFlowId(testIdentifier))
            }
        }
        emit(FlowStarted(testIdentifier.teamCityFlowId, testIdentifier.teamCityParentFlowId))
    }

    override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
        if (!enabled) return
        // Skipped tests are never reported as started/finished via this listener's executionStarted/executionFinished.
        // TeamCity also accepts testIgnored events without corresponding testStarted/testFinished, so we're good.
        // However, we still have to report the flow start/end here to tie it to the test suite.
        emit(FlowStarted(testIdentifier.teamCityFlowId, testIdentifier.teamCityParentFlowId))
        // TeamCity seems to only care about ignored tests (not ignored suites), but it might not harm to report it.
        // Therefore, no need for the distinction on the identifier type.
        emit(TestIgnored(testIdentifier.teamCityName, reason).withFlowId(testIdentifier))
        emit(FlowFinished(testIdentifier.teamCityFlowId))
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (!enabled) return
        when (testIdentifier.type) {
            TestDescriptor.Type.CONTAINER -> {
                emit(FlowFinished(testIdentifier.teamCityFlowId))
                emit(TestSuiteFinished(testIdentifier.teamCityName).withFlowId(testIdentifier))
            }
            TestDescriptor.Type.TEST,
            TestDescriptor.Type.CONTAINER_AND_TEST -> {
                when (testExecutionResult.status) {
                    TestExecutionResult.Status.SUCCESSFUL -> {} // just 'finished' is considered success
                    TestExecutionResult.Status.FAILED -> {
                        emit(testFailedMessage(testIdentifier, testExecutionResult, "Test failed without exception"))
                    }
                    TestExecutionResult.Status.ABORTED -> {
                        emit(testFailedMessage(testIdentifier, testExecutionResult, "Test was aborted"))
                    }
                }
                emit(FlowFinished(testIdentifier.teamCityFlowId))
                emit(TestFinished(testIdentifier.teamCityName, elapsedMillis(testIdentifier)).withFlowId(testIdentifier))
            }
        }
    }

    private fun testFailedMessage(
        testIdentifier: TestIdentifier,
        testExecutionResult: TestExecutionResult,
        messageIfNoException: String,
    ): ServiceMessage {
        val throwable = testExecutionResult.throwable.getOrNull()
        return if (throwable != null) {
            TestFailed(testIdentifier.teamCityName, throwable)
        } else {
            TestFailed(testIdentifier.teamCityName, messageIfNoException)
        }.withFlowId(testIdentifier)
    }

    override fun reportingEntryPublished(testIdentifier: TestIdentifier, entry: ReportEntry) {
        if (!enabled) return
        entry.keyValuePairs.forEach { (key, value) ->
            val message = when (key) {
                "stdout" -> TestStdOut(testIdentifier.teamCityName, value)
                "stderr" -> TestStdErr(testIdentifier.teamCityName, value)
                else -> TestMetadata(
                    testName = testIdentifier.teamCityName,
                    name = key,
                    value = value,
                )
            }
            emit(message.withFlowId(testIdentifier).withTimestamp(entry.timestamp))
        }
    }

    private fun emit(serviceMessage: ServiceMessage) {
        serviceMessagesStream.println(serviceMessage.asString())
    }

    private fun markStart(test: TestIdentifier) {
        startTimes[test.uniqueIdObject] = TimeSource.Monotonic.markNow()
    }

    private fun elapsedMillis(test: TestIdentifier): Int =
        startTimes[test.uniqueIdObject]?.elapsedNow()?.inWholeMilliseconds?.toInt()
            ?: error("Start time was not registered for test ${test.uniqueId} ('${test.displayName}')")
}

private fun ServiceMessage.withTimestamp(datetime: LocalDateTime): ServiceMessage {
    setTimestamp(datetime.toJavaDateInSystemTimeZone())
    return this
}

private fun LocalDateTime.toJavaDateInSystemTimeZone(): Date {
    val instant = atZone(ZoneOffset.systemDefault()).toInstant()
    return Date(instant.toEpochMilli())
}

private fun ServiceMessage.withFlowId(testIdentifier: TestIdentifier): ServiceMessage {
    setFlowId(testIdentifier.uniqueId)
    return this
}

/**
 * The name of the test in identified by this ID, in the format expected by Teamcity. It should look like this:
 *
 * `<suite name>: <package/namespace name>.<class name>.<test method>(<test parameters>)`
 *
 * See [the TeamCity docs](https://www.jetbrains.com/help/teamcity/service-messages.html#Interpreting+Test+Names).
 */
private val TestIdentifier.teamCityName: String
    get() = when (val s = source.getOrNull()) {
        is MethodSource -> "${s.className}.${s.methodName}(${s.methodParameterTypes})"
        is ClassSource -> s.className
        else -> displayName
    }

private val TestIdentifier.teamCityFlowId: String
    get() = uniqueId

private val TestIdentifier.teamCityParentFlowId: String?
    get() = parentId.getOrNull()

// TODO find the proper format
private fun TestIdentifier.toTeamCityLocationHint(): String? {
    return null
}

/**
 * Represents a test metadata message as defined in
 * [the TeamCity docs](https://www.jetbrains.com/help/teamcity/2024.12/reporting-test-metadata.html).
 */
private class TestMetadata(
    testName: String,
    name: String,
    value: String,
    type: String? = null,
) : MessageWithAttributes(
    ServiceMessageTypes.TEST_METADATA,
    buildMap {
        put("testName", testName)
        put("name", name)
        put("value", value)
        if (type != null) {
            put("type", type)
        }
    },
)

/**
 * Represents a "flow started" message as defined in
 * [the TeamCity docs](https://www.jetbrains.com/help/teamcity/2024.12/service-messages.html#Message+FlowId).
 */
private class FlowStarted(
    flowId: String,
    parent: String? = null,
) : MessageWithAttributes(
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
private class FlowFinished(
    flowId: String,
) : MessageWithAttributes(
    ServiceMessageTypes.FLOW_FINSIHED,
    buildMap {
        put("flowId", flowId)
    },
)
