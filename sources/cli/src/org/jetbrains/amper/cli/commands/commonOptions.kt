/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.Platform

private val prettyLeafPlatforms = Platform.leafPlatforms.associateBy { it.pretty }
private val prettyLeafPlatformsString = prettyLeafPlatforms.keys.sorted().joinToString(" ")

internal fun ParameterHolder.leafPlatformOption(help: String) = option(
    "-p",
    "--platform",
    help = help,
    completionCandidates = CompletionCandidates.Fixed(prettyLeafPlatforms.keys),
).convert { checkAndGetPlatform(it) }

/**
 * Check if the passed value can be converted to a platform and return one, if possible.
 * Throw exception otherwise.
 */
private fun checkAndGetPlatform(value: String) =
    prettyLeafPlatforms[value]
        ?: userReadableError("Unsupported platform '$value'.\n\nPossible values: $prettyLeafPlatformsString")
