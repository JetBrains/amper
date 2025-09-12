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
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.green
import org.jetbrains.amper.cli.commands.PlatformGroup.AliasOrInvalid
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withPlatformSuggestions
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.util.BuildType
import kotlin.collections.sorted

private val checkPlatformsListMsg =
    "Check the full list of supported platforms in the documentation:\n${Platform.docsUrl}"

/**
 * Accepts a leaf platform, with proper completion and typo correction suggestions.
 *
 * For commands that need multiple platforms, use this option with the
 * [multiple][com.github.ajalt.clikt.parameters.options.multiple] modifier so it can be repeated by users.
 * For consistency, do not use comma-separated lists.
 */
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

internal const val PlatformGroupOption = "--platform-group"

/**
 * Accepts a [Platform] or alias name representing one leaf or a combination of platforms.
 *
 * **Important**: this is not meant to be used to get a general set of platforms. For that purpose, use
 * [leafPlatformOption] with [multiple][com.github.ajalt.clikt.parameters.options.multiple] instead.
 */
internal fun BaseCliktCommand<*>.platformGroupOption(help: String): NullableOption<PlatformGroup, PlatformGroup> {
    val choices = Platform.values.associateBy { it.pretty.lowercase() }
    return option(
        "-p",
        PlatformGroupOption,
        help = "${help.ensureEndsWith(".")}\n\n$checkPlatformsListMsg",
    ).convert(
        metavar = "<platform-group>",
        // We can't show aliases here, but at least it helps 99% of users
        completionCandidates = Fixed(choices.keys),
    ) { text ->
        choices[text.lowercase()]?.let(PlatformGroup::BuiltIn) ?: AliasOrInvalid(text)
    }.also {
        configureContext {
            suggestTypoCorrection = suggestTypoCorrection.withPlatformSuggestions()
        }
    }
}

/**
 * Represents a combination of platforms, either as a built-in [Platform] value or as an alias.
 */
internal sealed interface PlatformGroup {
    /**
     * The alias or platform's user-readable name.
     */
    val name: String

    /**
     * Represents a plain string that is not a valid platform name, but could be a potential alias.
     * It can only be known when considering a particular module.
     */
    data class AliasOrInvalid(override val name: String) : PlatformGroup

    /**
     * A built-in [Platform] value, which represents either a group or a single leaf platform.
     */
    data class BuiltIn(val platform: Platform) : PlatformGroup {
        override val name: String
            get() = platform.pretty
    }
}

/**
 * Returns the leaf platforms from this group that are present in the given [module]'s declared platforms.
 */
context(command: BaseCliktCommand<*>)
internal fun PlatformGroup.validLeavesIn(module: AmperModule): Set<Platform> = when (this) {
    is AliasOrInvalid -> module.aliases[name] ?: command.currentContext.errorInvalidPlatformGroupName(module, name)
    is PlatformGroup.BuiltIn -> module.leafPlatforms intersect Platform.naturalHierarchyExt.getValue(platform)
}

private fun Context.errorInvalidPlatformGroupName(module: AmperModule, invalidName: String): Nothing {
    val validNames = module.validPlatformGroupNames()
    val didYouMean = didYouMeanMessage(invalidName, validNames)?.let { " $it" } ?: ""
    userReadableError("""
        Invalid platform group name '${red(invalidName)}'.$didYouMean
        
        Supported platform groups for module '${module.userReadableName}': ${validNames.sorted().joinToString()}
    """.trimIndent())
}

private fun AmperModule.validPlatformGroupNames() =
    aliases.keys + leafPlatforms.flatMap { it.pathToParent }.map { it.schemaValue }

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
    val didYouMean = context.didYouMeanMessage(choice, choices.keys) ?: ""
    return "invalid choice: $choice. $didYouMean\n\n$supportedValuesMsg"
}

/**
 * Returns a "did you mean X" message based on typo suggestions given the [invalidValue] and the [possibleValues].
 */
private fun Context.didYouMeanMessage(invalidValue: String, possibleValues: Set<String>): String? {
    val typoPossibilities = suggestTypoCorrection(invalidValue, possibleValues.sorted())
    return when (typoPossibilities.size) {
        0 -> null
        1 -> "Did you mean ${green(typoPossibilities.single())}?"
        else -> "Did you mean one of ${typoPossibilities.sorted().joinToString { green(it) }}?"
    }
}

internal const val UserJvmArgsOption = "--jvm-args"

internal fun ParameterHolder.userJvmArgsOption(help: String) = option(UserJvmArgsOption, help = help)
    .transformAll { values ->
        values.flatMap { it.splitArgsHonoringQuotes() }
    }

internal fun String.splitArgsHonoringQuotes(): List<String> {
    val args = mutableListOf<String>()
    val currentArg = StringBuilder()
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

internal fun ParameterHolder.buildTypeOption(
    help: String,
) = option(
    "-v",
    "--variant",
    help = help,
).enum<BuildType> { it.value }