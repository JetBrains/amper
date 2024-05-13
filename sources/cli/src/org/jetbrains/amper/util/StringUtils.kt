package org.jetbrains.amper.util

import java.nio.file.Path
import java.util.regex.Pattern

fun String.ensureEndsWith(suffix: String) =
    if (!endsWith(suffix)) (this + suffix) else this

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

    check(missingPlaceholders.isEmpty()) {
        "Missing placeholders [${missingPlaceholders.joinToString(" ")}] in template"
    }

    val escapedPlaceHolder = Pattern.quote(placeholder)
    val regex = Regex("$escapedPlaceHolder.+$escapedPlaceHolder")
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

    java.nio.file.Files.createDirectories(outputFile.parent)
    java.nio.file.Files.writeString(outputFile, result)
}
