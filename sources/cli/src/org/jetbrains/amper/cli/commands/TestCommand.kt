/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonRunSettings
import org.slf4j.LoggerFactory
import kotlin.collections.isNotEmpty

internal class TestCommand : AmperSubcommand(name = "test") {

    private val platforms by leafPlatformOption(help = "only run tests for the specified platform. The option can be repeated to test several platforms.")
        .multiple()

    private val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")

    private val jvmArgs by userJvmArgsOption(help = "The JVM arguments to pass to the JVM running the tests (only affects JVM tests)")

    private val includeModules by option("-m", "--include-module", help = "specific module to check, the option could be repeated to check several modules").multiple()

    private val excludeModules by option("--exclude-module", help = "specific module to exclude, the option could be repeated to exclude several modules").multiple()

    override fun help(context: Context): String = "Run tests in the project"

    override suspend fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }
        if (jvmArgs.isNotEmpty() && platforms.isNotEmpty() && Platform.JVM !in platforms && Platform.ANDROID !in platforms) {
            LoggerFactory.getLogger(javaClass).warn("--jvm-args have no effect when running only non-JVM tests")
        }

        // try to execute as many tests as possible
        withBackend(
            commonOptions,
            commandName,
            taskExecutionMode = TaskExecutor.Mode.GREEDY,
            commonRunSettings = CommonRunSettings(userJvmArgs = jvmArgs),
        ) { backend ->
            backend.test(
                requestedPlatforms = platforms.ifEmpty { null }?.toSet(),
                includeModules = if (includeModules.isNotEmpty()) includeModules.toSet() else null,
                excludeModules = excludeModules.toSet(),
            )
        }
    }
}
