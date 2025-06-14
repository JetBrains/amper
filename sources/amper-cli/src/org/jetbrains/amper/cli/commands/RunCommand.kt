/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.tasks.AllRunSettings

internal class RunCommand : AmperSubcommand(name = "run") {

    private val module by option("-m", "--module", help = "Specific module to run (run the `show modules` command to get the modules list)")

    private val platform by leafPlatformOption(help = "Run the app on specified platform. This option is only necessary if " +
            "the module has multiple main functions for different platforms")

    private val deviceId by option(
        "-d", "--device-id",
        help = """
            Platform specific device ID of the device to install and run on. 
            Only Android and iOS platforms are currently supported.
            - Android: use `adb devices` command to list connected devices and emulators
            - iOS: use `xcrun devicectl list devices` command to list available devices or `xcrun simctl list devices` to list available simulators.
        """.trimIndent(),
    )

    private val variant by buildTypeOption(
        help = "Run the specified variant of the app. Debug variant is launched by default.",
    )

    private val jvmArgs by userJvmArgsOption(
        help = """
            The JVM arguments to pass to the JVM running the application, separated by spaces.
            These arguments only affect the JVM used to run the application, and don't affect non-JVM applications.
            
            If the `$UserJvmArgsOption` option is repeated, the arguments contained in all occurrences are passed
            to the JVM in the order they were specified. The JVM decides how it handles duplicate arguments.
        """.trimIndent()
    )

    private val jvmMainClass by option("--main-class", help = "The fully-qualified name of the main class to run. This option is only applicable for JVM applications. By default, the main class is read from the module configuration file, or is determined automatically by convention, searching for a main.kt file.")

    private val workingDir by option("--working-dir", help = "The working directory for the application run. " +
            "By default, the current directory is used. This option is only applicable for JVM and native desktop " +
            "applications (the working directory is not customizable in mobile emulator runs).")
        .path(mustExist = true, canBeFile = false, canBeDir = true)

    private val programArguments by argument(name = "app_arguments").multiple()

    override fun help(context: Context): String = "Run your application"

    override fun helpEpilog(context: Context): String = "Use `--` to separate the application's arguments from Amper options"

    override suspend fun run() {
        withBackend(
            commonOptions = commonOptions,
            commandName = commandName,
            terminal = terminal,
            runSettings = AllRunSettings(
                programArgs = programArguments,
                explicitWorkingDir = workingDir,
                userJvmArgs = jvmArgs,
                userJvmMainClass = jvmMainClass,
                deviceId = deviceId,
            ),
        ) { backend ->
            backend.runApplication(moduleName = module, platform = platform, buildType = variant)
        }
    }
}
