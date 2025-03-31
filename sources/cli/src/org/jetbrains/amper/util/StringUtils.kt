package org.jetbrains.amper.util

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private val logger = LoggerFactory.getLogger("TemplateSubstitution")

@Suppress("SameParameterValue")
fun substituteTemplatePlaceholders(
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

/**
 * Regex matching ANSI Control Sequence Introducer (CSI) codes.
 * The format is the following:
 *
 * * the `ESC [` introducer (`\u001B` escape character, followed by `[`)
 * * then any number (including none) of "parameter bytes" in the range 0x30–0x3F (ASCII `0-9:;<=>?`)
 * * then any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and `!"#$%&'()*+,-./`)
 * * then a single "final byte" in the range 0x40–0x7E (ASCII `@A-Z[\]^_``a-z{|}~`).
 *
 * See [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code).
 */
private val ansiCsiRegex = Regex("""\u001B\[[0-9:;<=>?]*[ !"#$%&'()*+,\-./]*[@A-Z\[\\\]^_`a-z{|}~]""")

/**
 * Returns this string with all ANSI codes removed.
 */
internal fun String.filterAnsiCodes(): String = replace(ansiCsiRegex, "")
