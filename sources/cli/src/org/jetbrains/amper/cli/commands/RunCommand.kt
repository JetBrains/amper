/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.util.BuildType

internal class RunCommand : AmperSubcommand(name = "run") {

    private val module by option("-m", "--module", help = "Specific module to run (run the 'modules' command to get the modules list)")

    private val platform by leafPlatformOption(help = "Run the app on specified platform. This option is only necessary if " +
            "the module has multiple main functions for different platforms")

    private val buildType by option(
        "-b",
        "--build-type",
        help = "Run the app with the specified build type (${BuildType.buildTypeStrings.sorted().joinToString(", ")})",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings),
    )
        .convert { BuildType.byValue(it) ?: fail("'$it'.\n\nPossible values: ${BuildType.buildTypeStrings}") }
        .default(BuildType.Debug)

    private val programArguments by argument(name = "program arguments").multiple()

    override fun help(context: Context): String = "Run your application"

    override fun helpEpilog(context: Context): String = "Use -- to separate the application's arguments from Amper options"

    override suspend fun run() {
        withBackend(
            commonOptions,
            commandName,
            commonRunSettings = CommonRunSettings(programArgs = programArguments),
        ) { backend ->
            backend.runApplication(moduleName = module, platform = platform, buildType = buildType)
        }
    }
}
