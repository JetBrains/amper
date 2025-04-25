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
import org.junit.platform.launcher.TestPlan
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
    private val alsoPrintNormalOutput = System.getProperty("$configPropertyGroup.alsoPrintNormalOutput", "false").toBooleanStrict()

    private val currentTest = InheritableThreadLocal<TestIdentifier?>()

    private val watchedStdout = watchedStream(
        original = System.out,
        serviceMessagesStream = serviceMessagesStream,
        forwardToOriginalStream = alsoPrintNormalOutput,
        currentTest = currentTest,
    ) { testId, output ->
        emit(TestStdOut(testId.teamCityName, output).withFlowId(testId))
    }

    private val watchedStderr = watchedStream(
        original = System.err,
        serviceMessagesStream = serviceMessagesStream,
        forwardToOriginalStream = alsoPrintNormalOutput,
        currentTest = currentTest,
    ) { testId, output ->
        emit(TestStdErr(testId.teamCityName, output).withFlowId(testId))
    }

    /**
     * The time at which each test or container started, to use for test duration calculations.
     */
    private val startTimes = ConcurrentHashMap<UniqueId, TimeMark>()

    /**
     * An opaque and unique ID for the current test plan.
     */
    private lateinit var testPlanId: String

    init {
        if (enabled) {
            // we don't use the built-in stream capture of JUnit because it only reports it at the end of each test
            // (it doesn't stream it). See https://github.com/junit-team/junit5/issues/4317
            System.setOut(watchedStdout)
            System.setErr(watchedStderr)
        }
    }

    override fun testPlanExecutionStarted(testPlan: TestPlan?) {
        testPlanId = UUID.randomUUID().toString()
    }

    /**
     * A flow ID to identify this test or container in TeamCity.
     * This ID must be unique within the TeamCity build, but also be consistent
     * (the same [TestIdentifier] should yield the same result).
     */
    private val TestIdentifier.teamCityFlowId: String
        get() = "$uniqueId-$testPlanId"

    /**
     * Equivalent to [teamCityFlowId] but for the parent identifier of this test.
     */
    private val TestIdentifier.teamCityParentFlowId: String?
        get() = parentId.getOrNull()?.let { "$it-$testPlanId" }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (!enabled) return
        emit(FlowStarted(testIdentifier.teamCityFlowId, testIdentifier.teamCityParentFlowId))
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // TestIdentifier can't be constructed with the `null` `type`
        when (testIdentifier.type) {
            TestDescriptor.Type.CONTAINER -> {
                emit(TestSuiteStarted(testIdentifier.teamCityName).withFlowId(testIdentifier))
            }
            TestDescriptor.Type.TEST,
            TestDescriptor.Type.CONTAINER_AND_TEST -> {
                currentTest.set(testIdentifier)
                markStart(testIdentifier)
                // disable automatic stdout/stderr capture between start&stop messages
                // (we use proper service messages to report it, so no need for guesswork)
                val captureStdOutput = false
                val locationHint = testIdentifier.toTeamCityLocationHint()
                emit(TestStarted(testIdentifier.teamCityName, captureStdOutput, locationHint).withFlowId(testIdentifier))
            }
        }
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
        // make sure partial output at the end of the tests is reported
        watchedStdout.forceFlush()
        watchedStderr.forceFlush()
        currentTest.set(null)
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // TestIdentifier can't be constructed with the `null` `type`
        when (testIdentifier.type) {
            TestDescriptor.Type.CONTAINER -> {
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
                emit(TestFinished(testIdentifier.teamCityName, elapsedMillis(testIdentifier)).withFlowId(testIdentifier))
            }
        }
        emit(FlowFinished(testIdentifier.teamCityFlowId))
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
            // We don't use the standard `stdout` and `stderr` keys (from the stream capture feature of JUnit)
            // in any special way anymore. We can't use them for TC reporting, because we want to report these
            // line-by-line as they come, but the default stream capture just sends one `stdout` event at the end of the
            // test (it was meant for XML reporting initially). See: https://github.com/junit-team/junit5/issues/4317.
            // Because of this, we have our own standard stream watchers, so we must not report 'stdout'/'stderr'
            // entries, otherwise we'll double the output.
            val message = when (key) {
                // Using custom keys because there is currently no standard key to represent streaming output.
                // See https://github.com/junit-team/junit5/issues/4323
                StreamingOutputKeys.STDOUT -> TestStdOut(testIdentifier.teamCityName, value)
                StreamingOutputKeys.STDERR -> TestStdErr(testIdentifier.teamCityName, value)
                else -> TestMetadata(testName = testIdentifier.teamCityName, name = key, value = value)
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

/**
 * Wraps the given [original] stream in a [ThreadAwareEavesdroppingPrintStream] that associate output to tests using the
 * given [currentTest] thread local.
 *
 * **Note:** this is not a 100% reliable solution, as it only associate the output with the test if it is printed from
 * the test's original thread, or a child of that thread.
 */
private fun watchedStream(
    original: PrintStream,
    serviceMessagesStream: PrintStream,
    forwardToOriginalStream: Boolean,
    currentTest: ThreadLocal<TestIdentifier?>,
    onLinePrinted: (TestIdentifier, String) -> Unit,
): ThreadAwareEavesdroppingPrintStream<TestIdentifier?> = ThreadAwareEavesdroppingPrintStream(
    original = original,
    forwardToOriginalStream = forwardToOriginalStream,
    // When the service messages stream is the same as the original stream, we can't print a service message after
    // a partial line of output, because the ##teamcity mark must be at the start of a line.
    // A `print("something")` must not result in `something##teamcity[stdOut ... "something"]`.
    // This is why, in this case, we only flush when we have a complete line because it means the original stream
    // also has a complete line, thus ensuring proper interlacing of output lines and service messages.
    // Nevertheless, we use forceFlush() at the end of a test to report a potential trailing partial line.
    allowPartialLineFlush = !forwardToOriginalStream || serviceMessagesStream != original,
    threadLocalKey = currentTest,
) { testId, output ->
    if (testId != null) {
        if (serviceMessagesStream == original && !output.endsWith('\n')) {
            // terminate the output line before printing the service message (to avoid ##teamcity in the middle)
            serviceMessagesStream.println()
        }
        onLinePrinted(testId, output)
    }
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
