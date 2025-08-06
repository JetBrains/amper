/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.enum
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.engine.PackageTask

internal class PackageCommand : AmperProjectAwareCommand(name = "package") {

    private val modules by option(
        "-m", "--module",
        help = "The specific module to package (run the `show modules` command to get the modules list). " +
                "This option can be repeated to package several modules."
    ).multiple().unique()

    private val platforms by leafPlatformOption(
        help = "The target platform to package for. This option can be repeated to package for several platforms.",
    ).multiple().unique()

    private val buildTypes by buildTypeOption(
        help = "The variant to package. This option can be repeated to package several variants. " +
                "Release variant is packaged by default."
    ).multiple().unique()

    private val formats by option(
        "-f",
        "--format",
        help = "The output package format.",
    )
        .enum<PackageTask.Format> { it.value }
        .multiple()
        .unique()

    override fun help(context: Context): String = "Package the project artifacts for distribution"

    override suspend fun run(cliContext: CliContext) {
        withBackend(cliContext) { backend ->
            backend.`package`(
                platforms = platforms.takeIf { it.isNotEmpty() },
                modules = modules.takeIf { it.isNotEmpty() },
                buildTypes = buildTypes.takeIf { it.isNotEmpty() },
                formats = formats.takeIf { it.isNotEmpty() },
            )
        }
        printSuccessfulCommandConclusion("Build successful")
    }
}
