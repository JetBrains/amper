/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TestResultsFormat
import org.jetbrains.amper.test.FilterMode
import org.jetbrains.amper.test.TestFilter
import kotlin.collections.isNotEmpty

/**
 * The Unicode Next Line character, to add new lines in help texts while still respecting the terminal width.
 *
 * See https://ajalt.github.io/clikt/documenting/#manual-line-breaks
 */
private const val NEL = '\u0085'

internal class TestCommand : AmperSubcommand(name = "test") {

    private val platforms by leafPlatformOption(help = "only run tests for the specified platform. The option can be repeated to test several platforms.")
        .multiple()

    // Note: we can't use patterns for test methods because JUnit Console Launcher only supports literals for this
    private val includeTestFilters by option("--include-test",
        metavar = "TEST_FQN",
        help = "Only run the given test. The option can be repeated to run multiple specific tests.$NEL" +
                "The value should be the fully qualified name of a test method, including its package, containing " +
                "class (or top-level test suite function), and optionally a list of parameter types. If the test is " +
                "in a nested class, use '/' to separate the containing class from the nested class name.$NEL" +
                "$NEL" +
                "Example: 'com.example.MyTest/MyNestedClass.myTestMethod(com.example.Param1Type,com.example.Param2Type)'.$NEL" +
                "$NEL" +
                "If some --include-classes options are provided, the given specific test is run in addition to the " +
                "tests matched via the patterns.",
    )
        .convert { TestFilter.includeTest(it) }
        .multiple()

    private val includeClassFilters by option("--include-classes",
        metavar = "PATTERN",
        help = "Only run tests classes or suites matching the given pattern. " +
                "The option can be repeated to include test classes matching any pattern (OR semantics). " +
                "The pattern should be the fully qualified name of a test class or top-level test suite function, " +
                "optionally containing '*' or '?' wildcards to match multiple or a single character, respectively. " +
                "Nested classes should be specified using the '/' separator. " +
                "Wildcards match any parts of the name, even across '.' and '/' separators. " +
                "If the --exclude-classes option is also provided, tests are run if their containing class/suite " +
                "matches any of the include patterns AND doesn't match any of the exclude patterns.",
    )
        .convert { TestFilter.includeOrExcludeSuite(pattern = it, mode = FilterMode.Include) }
        .multiple()

    private val excludeClassFilters by option("--exclude-classes",
        metavar = "PATTERN",
        help = "Do not run test classes or suites matching the given pattern. " +
                "The option can be repeated to exclude test classes matching any pattern. " +
                "The pattern should be the fully qualified name of a test class or top-level test suite function, " +
                "optionally containing '*' or '?' wildcards to match multiple or a single character, respectively. " +
                "Nested classes should be specified using the '/' separator. " +
                "Wildcards match any parts of the name, even across '.' and '/' separators. " +
                "If the --include-classes option is also provided, tests are run if their containing class/suite " +
                "matches any of the include patterns AND doesn't match any of the exclude patterns.",
    )
        .convert { TestFilter.includeOrExcludeSuite(pattern = it, mode = FilterMode.Exclude) }
        .multiple()

    private val jvmArgs by userJvmArgsOption(
        help = "The JVM arguments to pass to the JVM running the tests, separated by spaces. " +
                "These arguments only affect JVM and Android tests; they don't affect non-JVM tests (such as iOS tests). " +
                "If the $UserJvmArgsOption option is repeated, the arguments contained in all occurrences are passed " +
                "to the JVM in the order they were specified. The JVM decides how it handles duplicate arguments."
    )

    private val includeModules by option("-m", "--include-module",
        metavar = "MODULE",
        help = "Only run tests from the given module. The option can be repeated to run tests from several modules."
    ).multiple()

    private val excludeModules by option(
        "--exclude-module",
        metavar = "MODULE",
        help = "Do not run tests from the given module. The option can be repeated to exclude several modules. " +
                "If the --include-module option is also provided, tests are run if their module is included AND is " +
                "not excluded."
    ).multiple()

    private val format by option(
        "--format",
        metavar = "FORMAT",
        help = "The format to use for test results. " +
                "'pretty' is a human-readable format for local CLI runs. " +
                "'events' is for machine readable events that can be interpreted by some CI systems and IDEs."
    ).enum<TestResultsFormat> { it.cliValue }.default(TestResultsFormat.Pretty)

    override fun help(context: Context): String = "Run tests in the project"

    override suspend fun run() {
        val allTestFilters = includeTestFilters + includeClassFilters + excludeClassFilters
        withBackend(
            commonOptions = commonOptions,
            currentCommand = commandName,
            terminal = terminal,
            taskExecutionMode = TaskExecutor.Mode.GREEDY, // try to execute as many tests as possible
            commonRunSettings = CommonRunSettings(
                userJvmArgs = jvmArgs,
                testFilters = allTestFilters,
                testResultsFormat = format,
            ),
        ) { backend ->
            if (allTestFilters.isNotEmpty() && includeModules.isEmpty() && excludeModules.isEmpty() && backend.modules().size > 1) {
                userReadableError(
                    "When using test filters, it is required to use --include-module or --exclude-module to also select " +
                            "the modules where matching tests are expected. " +
                            "Including modules with no matching tests will result in an error.")
            }

            backend.test(
                requestedPlatforms = platforms.ifEmpty { null }?.toSet(),
                includeModules = if (includeModules.isNotEmpty()) includeModules.toSet() else null,
                excludeModules = excludeModules.toSet(),
            )
        }
    }
}
