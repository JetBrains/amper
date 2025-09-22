/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private val logger = LoggerFactory.getLogger("TemplateSubstitution")

@Suppress("SameParameterValue")
internal fun substituteTemplatePlaceholders(
    input: String,
    outputFile: Path,
    replacementRules: List<Pair<String, String>>,
) {
    val result = input
        .replaceMultiple(replacementRules)
        .replace05ExitCommandPadding()

    val unsubstituted = result
        .lineSequence()
        .mapIndexed { line, s -> "line ${line + 1}: $s" }
        .filter(Regex("@\\S+@")::containsMatchIn)
        .joinToString("\n")
    check(unsubstituted.isBlank()) {
        "Some template parameters were left unsubstituted in template:\n$unsubstituted"
    }

    outputFile.parent.createDirectories()
    outputFile.writeText(result)
}

private fun String.replaceMultiple(replacementRules: List<Pair<String, String>>): String =
    replacementRules.fold(this) { text, rule ->
        val (placeholder, replacement) = rule
        if (placeholder !in text) {
            logger.warn("Placeholder '$placeholder' is not in the input")
        }
        text.replace(placeholder, replacement)
    }

/**
 * See comment in amper.template.bat around the placeholder.
 */
private const val exitCommandPaddingPlaceholder = "@EXIT_COMMAND_PADDING@"
private const val exitCommandTargetOffset = 6826

/**
 * See comment in amper.template.bat around the placeholder.
 */
private fun String.replace05ExitCommandPadding(): String {
    val index = indexOf(exitCommandPaddingPlaceholder)
    if (index < 0) return this

    val paddingToTargetOffset = exitCommandTargetOffset - index
    check(paddingToTargetOffset > 0) {
        "Cannot add padding to reach target offset $exitCommandTargetOffset, the placeholder is already at $index. " +
                "Please move the exit command from the comment further up the wrapper template"
    }
    return replace(exitCommandPaddingPlaceholder, " ".repeat(paddingToTargetOffset))
}
