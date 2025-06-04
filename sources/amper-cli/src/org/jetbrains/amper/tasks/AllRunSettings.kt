/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.test.TestFilter
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Settings that are passed from the command line to user-visible processes that Amper runs, such as tests or the
 * user's applications.
 */
sealed interface RunSettings

/**
 * Settings for regular desktop/CLI application runs.
 */
sealed interface DesktopRunSettings : RunSettings {
    /**
     * The arguments to use when running the user's JVM or native application.
     */
    val programArgs: List<String>

    /**
     * The explicitly specified working directory to use when running the user's application, or null if not specified.
     *
     * @see workingDir
     */
    val explicitWorkingDir: Path?
}

/**
 * The working directory to use when running the user's application.
 */
val DesktopRunSettings.workingDir: Path
    get() = explicitWorkingDir ?: Path(System.getProperty("user.dir"))

/**
 * Settings for mobile application runs (in a device or emulator, not on the host).
 */
sealed interface MobileRunSettings : RunSettings {

    /**
     * User provided platform-specific string that identifies the device (physical or emulator) to run on.
     */
    val deviceId: String?
}

/**
 * Settings for any test run (common to all platforms).
 */
sealed interface TestRunSettings : RunSettings {
    /**
     * Filters to select or exclude tests.
     */
    val testFilters: List<TestFilter>

    /**
     * How the test results should be formatted.
     */
    val testResultsFormat: TestResultsFormat
}

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

/**
 * Settings for any JVM process run (can be user applications or JVM tests).
 */
sealed interface JvmRunSettings : RunSettings {
    /**
     * The JVM args passed by the user.
     *
     * They should be used in JVMs that we launch on behalf of the user and that are exposed to the user.
     * Namely, JVMs used to run the user's JVM application or to run some JVM tests.
     */
    val userJvmArgs: List<String>
}

/**
 * Settings for JVM runs using the regular main class/main function mechanism.
 */
sealed interface JvmMainRunSettings : JvmRunSettings, DesktopRunSettings {

    /**
     * The JVM main class passed by the user
     *
     * It's required to override the effective main class set by the configuration for two main reasons:
     *  1. For the IDE to be able to run any class by gutter icon
     *  2. For hot-reload using dev entry point (especially for Compose libs, where you have no jvm app to run)
     */
    val userJvmMainClass: String?
}

/**
 * Settings for JVM test runs.
 */
sealed interface JvmTestRunSettings : JvmRunSettings, TestRunSettings

/**
 * Settings for any Kotlin/Native process run (can be user applications or test executables).
 */
sealed interface NativeRunSettings : RunSettings

/**
 * Settings for Kotlin/Native application runs (for desktop targets only, not iOS).
 */
sealed interface NativeDesktopRunSettings : NativeRunSettings, DesktopRunSettings

/**
 * Settings for Kotlin/Native tests.
 */
sealed interface NativeTestRunSettings : NativeRunSettings, TestRunSettings

/**
 * Settings that are passed from the command line to user-visible processes that Amper runs, such as tests or the
 * user's applications.
 *
 * This class implements all interfaces for different cases, because we always need to create the whole task graph,
 * including test and run tasks, even though we only run either the `test` command, `run` command, or another command.
 */
data class AllRunSettings(
    override val programArgs: List<String> = emptyList(),
    override val explicitWorkingDir: Path? = null,
    override val testFilters: List<TestFilter> = emptyList(),
    override val testResultsFormat: TestResultsFormat = TestResultsFormat.Pretty,
    override val userJvmArgs: List<String> = emptyList(),
    override val userJvmMainClass: String? = null,
    override val deviceId: String? = null,
) : JvmMainRunSettings, NativeDesktopRunSettings, MobileRunSettings, JvmTestRunSettings, NativeTestRunSettings
