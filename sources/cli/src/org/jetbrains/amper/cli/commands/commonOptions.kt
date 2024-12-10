/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
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

internal const val UserJvmArgsOption = "--jvm-args"

internal fun ParameterHolder.userJvmArgsOption(help: String) = option(UserJvmArgsOption, help = help)
    .transformAll { values ->
        values.flatMap { it.splitArgsHonoringQuotes() }
    }

internal fun String.splitArgsHonoringQuotes(): List<String> {
    val args = mutableListOf<String>()
    var currentArg = StringBuilder()
    var inQuotes = false
    var escaping = false
    var hasPendingArg = false

    for (c in this) {
        if (escaping) {
            currentArg.append(c)
            escaping = false
            continue
        }
        when {
            c == '\\' -> {
                escaping = true
            }
            c == '"' -> {
                inQuotes = !inQuotes
                hasPendingArg = true
            }
            c == ' ' && !inQuotes -> {
                if (hasPendingArg) { // multiple spaces shouldn't yield empty args
                    args.add(currentArg.toString())
                    currentArg.clear()
                    hasPendingArg = false
                }
            }
            else -> {
                currentArg.append(c)
                hasPendingArg = true
            }
        }
    }
    require(!escaping) { "Dangling escape character '\\'" }
    require(!inQuotes) { "Unclosed quotes" }
    if (hasPendingArg) {
        args.add(currentArg.toString())
    }
    return args
}
