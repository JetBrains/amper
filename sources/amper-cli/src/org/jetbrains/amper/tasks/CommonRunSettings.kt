/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.test.TestFilter

/**
 * Settings that are passed from the command line to user-visible processes that Amper runs, such as tests or the
 * user's applications.
 */
data class CommonRunSettings(
    /**
     * The arguments to use when running the user's JVM or native application.
     */
    val programArgs: List<String> = emptyList(),
    /**
     * Filters to select or exclude tests.
     */
    val testFilters: List<TestFilter> = emptyList(),
    /**
     * How the test results should be formatted.
     */
    val testResultsFormat: TestResultsFormat = TestResultsFormat.Pretty,
    /**
     * The JVM args passed by the user.
     *
     * They should be used in JVMs that we launch on behalf of the user and that are exposed to the user.
     * Namely, JVMs used to run the user's JVM application or to run some JVM tests.
     */
    val userJvmArgs: List<String> = emptyList(),

    /**
     *  The JVM main class passed by the user
     *  
     *  It's required to override the effective main class set by the configuration for two main reasons:
     *  1. For the IDE to be able to run any class by gutter icon
     *  2. For hot-reload using dev entry point (especially for Compose libs, where you have no jvm app to run)
     */
    val userJvmMainClass: String? = null,

    /**
     * User provided platform-specific string that identifies the device (physical or emulator) to run on.
     */
    val deviceId: String? = null,
)

enum class TestResultsFormat(val cliValue: String) {
    /**
     * Simple human-readable format for local CLI runs.
     */
    Pretty("pretty"),

    /**
     * TeamCity service message format, which is machine-readable and also understood by IntelliJ IDEA.
     */
    TeamCity("teamcity"),
}
