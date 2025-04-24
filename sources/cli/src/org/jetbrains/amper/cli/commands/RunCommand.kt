/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
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

    private val deviceId by option(
        "-d", "--device-id",
        help = "Platform specific device id to select the device to install and run on. " +
                "Only Android and iOS platforms are currently supported.\n" +
                "- Android: use `adb devices` command to list connected devices and emulators\n" +
                "- iOS: use `xcrun devicectl list devices` command to list available devices or " +
                "`xcrun simctl list devices` to list available simulators."
    )

    private val variant by option(
        "-v",
        "--variant",
        help = "Run the specified variant of the app (${BuildType.buildTypeStrings.sorted().joinToString(", ")})",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings),
    )
        .convert { BuildType.byValue(it) ?: fail("'$it'.\n\nPossible values: ${BuildType.buildTypeStrings}") }
        .default(BuildType.Debug)

    private val jvmArgs by userJvmArgsOption(
        help = "The JVM arguments to pass to the JVM running the application, separated by spaces. " +
                "These arguments only affect the JVM used to run the application, and don't affect non-JVM applications. " +
                "If the $UserJvmArgsOption option is repeated, the arguments contained in all occurrences are passed " +
                "to the JVM in the order they were specified. The JVM decides how it handles duplicate arguments."
    )

    private val jvmMainClass by option("--main-class", help = "Specifies the main class to run. This option is only applicable for JVM applications.")

    private val programArguments by argument(name = "app_arguments").multiple()

    override fun help(context: Context): String = "Run your application"

    override fun helpEpilog(context: Context): String = "Use -- to separate the application's arguments from Amper options"

    override suspend fun run() {
        withBackend(
            commonOptions = commonOptions,
            currentCommand = commandName,
            terminal = terminal,
            commonRunSettings = CommonRunSettings(
                programArgs = programArguments,
                userJvmArgs = jvmArgs,
                userJvmMainClass = jvmMainClass,
                deviceId = deviceId,
            ),
        ) { backend ->
            backend.runApplication(moduleName = module, platform = platform, buildType = variant)
        }
    }
}
