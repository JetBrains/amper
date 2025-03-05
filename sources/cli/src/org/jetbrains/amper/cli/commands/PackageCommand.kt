/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.choice
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.engine.PackageTask
import org.jetbrains.amper.util.BuildType

internal class PackageCommand : AmperSubcommand(name = "package") {

    private val modules by option(
        "-m", "--module",
        help = "The specific module to package (run the 'show modules' command to get the modules list). " +
                "This option can be repeated to package several modules."
    ).multiple().unique()

    private val platforms by leafPlatformOption(
        help = "Only package for the specified platform. " +
                "This option can be repeated to package for several platforms."
    )
        .multiple().unique()

    private val buildTypes by option(
        "-v",
        "--variant",
        help = "Package the specified variant (${BuildType.buildTypeStrings.sorted().joinToString(", ")}). " +
                "This option can be repeated to package several variants.",
        completionCandidates = CompletionCandidates.Fixed(BuildType.buildTypeStrings),
    )
        .convert { BuildType.byValue(it) ?: fail("'$it'.\n\nPossible values: ${BuildType.buildTypeStrings}") }
        .multiple()

    private val formats by option(
        "-f",
        "--format",
        help = "Specify the output format (${PackageTask.Format.formatStrings.sorted().joinToString(", ")}).",
    )
        .convert {
            PackageTask.Format.byValue(it) ?: fail("'$it'.\n\nPossible values: ${PackageTask.Format.formatStrings}")
        }
        .multiple()

    override fun help(context: Context): String = "Package the project artifacts for distribution"

    override suspend fun run() = withBackend(commonOptions, commandName) { backend ->
        backend.`package`(
            platforms = platforms.takeIf { it.isNotEmpty() },
            modules = modules.takeIf { it.isNotEmpty() },
            buildTypes = buildTypes.toSet().takeIf { it.isNotEmpty() },
            formats = formats.toSet().takeIf { it.isNotEmpty() },
        )
    }
}
