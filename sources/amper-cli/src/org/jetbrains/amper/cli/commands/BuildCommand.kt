/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import org.jetbrains.amper.cli.withBackend

internal class BuildCommand : AmperSubcommand(name = "build") {

    private val modules by option("-m", "--module",
        help = "The specific module to build (run the 'show modules' command to get the modules list). " +
                "This option can be repeated to build several modules."
    ).multiple().unique()

    private val platforms by leafPlatformOption(help = "Only build for the specified platform. " +
            "This option can be repeated to build several platforms.")
        .multiple().unique()

    private val buildTypes by buildTypeOption(
        help = "Build the specified variant. This option can be repeated to build several variants. " +
                "Debug variant is built by default.",
    ).multiple().unique()

    override fun help(context: Context): String = "Compile and link all code in the project"

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            backend.build(
                platforms = platforms.takeIf { it.isNotEmpty() },
                modules = modules.takeIf { it.isNotEmpty() },
                buildTypes = buildTypes.takeIf { it.isNotEmpty() },
            )
        }
        printSuccessfulCommandConclusion("Build successful")
    }
}
