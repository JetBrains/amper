/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates.Fixed
import com.github.ajalt.clikt.core.BaseCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.green
import org.jetbrains.amper.cli.withPlatformSuggestions
import org.jetbrains.amper.frontend.Platform
import kotlin.collections.sorted

private val checkPlatformsListMsg =
    "Check the full list of supported platforms in the documentation:\n${Platform.docsUrl}"

internal fun BaseCliktCommand<*>.leafPlatformOption(help: String) = option(
    "-p",
    "--platform",
    help = "${help.ensureEndsWith(".")}\n\n$checkPlatformsListMsg",
).choiceWithTypoSuggestion(
    choices = Platform.leafPlatforms.associateBy { it.pretty },
    metavar = "<platform>", // too many values to show them in the metavar, so we override with this placeholder
    ignoreCase = true,
    supportedValuesMsg = checkPlatformsListMsg,
).also {
    configureContext {
        suggestTypoCorrection = suggestTypoCorrection.withPlatformSuggestions()
    }
}

private fun String.ensureEndsWith(suffix: String): String = if (endsWith(suffix)) this else "$this$suffix"

/**
 * Exactly like the original [choice] option type, but suggests typo corrections in case of invalid values.
 */
// Custom implementation while waiting for the built-in feature: https://github.com/ajalt/clikt/issues/592
internal fun <T : Any> RawOption.choiceWithTypoSuggestion(
    choices: Map<String, T>,
    metavar: String = choices.keys.joinToString("|", prefix = "(", postfix = ")"),
    ignoreCase: Boolean = false,
    supportedValuesMsg: String = "Supported values: ${choices.keys.sorted().joinToString()}"
): NullableOption<T, T> {
    require(choices.isNotEmpty()) { "Must specify at least one choice" }
    val c = if (ignoreCase) choices.mapKeys { it.key.lowercase() } else choices
    return convert(metavar, completionCandidates = Fixed(choices.keys)) {
        c[if (ignoreCase) it.lowercase() else it] ?: fail(errorMessage(context, it, choices, supportedValuesMsg))
    }
}

private fun errorMessage(
    context: Context,
    choice: String,
    choices: Map<String, *>,
    supportedValuesMsg: String,
): String {
    val allChoices = choices.keys.sorted()
    val typoPossibilities = context.suggestTypoCorrection(choice, allChoices)
    val didYouMean = when (typoPossibilities.size) {
        0 -> ""
        1 -> "Did you mean ${green(typoPossibilities.single())}?"
        else -> "Did you mean one of ${typoPossibilities.joinToString { green(it) }}?"
    }
    return "invalid choice: $choice. $didYouMean\n\n$supportedValuesMsg"
}

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
