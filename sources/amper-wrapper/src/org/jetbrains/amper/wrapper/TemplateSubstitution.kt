/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private val logger = LoggerFactory.getLogger("TemplateSubstitution")

@Suppress("SameParameterValue")
internal fun substituteTemplatePlaceholders(
    input: String,
    outputFile: Path,
    placeholder: String,
    values: List<Pair<String, String>>,
    outputWindowsLineEndings: Boolean = false,
) {
    var result = input.replace("\r\n", "\n")

    val missingPlaceholders = mutableListOf<String>()
    for ((name, value) in values) {
        check (!name.contains(placeholder)) {
            "Do not use placeholder '$placeholder' in name: $name"
        }

        val s = "$placeholder$name$placeholder"
        if (!result.contains(s)) {
            missingPlaceholders.add(s)
        }

        result = result.replace(s, value)
    }

    missingPlaceholders.forEach {
        logger.warn("Placeholder '$it' is not used in $outputFile")
    }

    result = result.replace05ExitCommandPadding()

    val escapedPlaceHolder = Pattern.quote(placeholder)
    val regex = Regex("$escapedPlaceHolder\\S+$escapedPlaceHolder")
    val unsubstituted = result
        .splitToSequence('\n')
        .mapIndexed { line, s -> "line ${line + 1}: $s" }
        .filter(regex::containsMatchIn)
        .joinToString("\n")
    check (unsubstituted.isBlank()) {
        "Some template parameters were left unsubstituted in template:\n$unsubstituted"
    }

    if (outputWindowsLineEndings) {
        result = result.replace("\n", "\r\n")
    }

    outputFile.parent.createDirectories()
    outputFile.writeText(result)
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
