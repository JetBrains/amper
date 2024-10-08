/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.util.BuildType

internal class RunCommand : SuspendingCliktCommand(name = "run") {

    val platform by leafPlatformOption(help = "Run the app on specified platform. This option is only necessary if " +
            "the module has multiple main functions for different platforms")

    val buildType by option(
        "-b",
        "--build-type",
        help = "Run under specified build type (${BuildType.buildTypeStrings.sorted().joinToString(", ")})",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings),
    ).default(BuildType.Debug.value).validate { value -> checkBuildType(value) }

    private fun checkBuildType(value: String) {
        BuildType.byValue(value)
            ?: userReadableError("Unsupported build type '$value'.\n\nPossible values: ${BuildType.buildTypeStrings}")
    }

    val programArguments by argument(name = "program arguments").multiple()

    val module by option("-m", "--module", help = "Specific module to run (run 'modules' command to get modules list)")

    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Run your application"

    override fun helpEpilog(context: Context): String = "Use -- to separate the application's arguments from Amper options"

    override suspend fun run() {
        withBackend(
            commonOptions,
            commandName,
            commonRunSettings = CommonRunSettings(programArgs = programArguments),
        ) { backend ->
            val buildType = buildType.let { BuildType.byValue(it) }
            backend.runApplication(platform = platform, moduleName = module, buildType = buildType)
        }
    }
}
