package org.jetbrains.deft.proto.frontend.helper

import org.jetbrains.deft.proto.core.messages.BuildProblem
import org.jetbrains.deft.proto.core.messages.render
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun checkDiagnostics(expectedTestDataFile: File, cleanedText: String, tempDir: Path, errors: List<BuildProblem>) {
    val expectedText = expectedTestDataFile.readText().trimTrailingWhitespacesAndEmptyLines()
    val actualText =
        annotateTextWithDiagnostics(cleanedText, errors) { it.replace(tempDir.absolutePathString(), "") }
            .trimTrailingWhitespacesAndEmptyLines()

    assertFileContentEquals(expectedText, actualText, expectedTestDataFile)
}

fun String.removeDiagnosticsAnnotations(): String =
    lines()
        .filter { !it.trim().matches(DIAGNOSTIC_REGEX) }
        .joinToString(separator = "\n")
        .trimTrailingWhitespacesAndEmptyLines()

fun annotateTextWithDiagnostics(
    intoText: String,
    diagnostics: List<BuildProblem>,
    sanitizeDiagnostic: (String) -> String
): String {
    val (diagnosticsWithLine, diagnosticsWithoutLine) =
        diagnostics.partition { it.line != null }

    val result = StringBuilder()
    result.appendDiagnostics(diagnosticsWithoutLine, withIndent = 0, sanitizeDiagnostic)

    val diagnosticsByLine = diagnosticsWithLine.groupBy { it.line!! - 1 }
    for ((originalLineNumber, line) in intoText.lines().withIndex()) {
        val diagnosticsForThisLine = diagnosticsByLine[originalLineNumber]
        if (diagnosticsForThisLine != null) {
            result.appendDiagnostics(diagnosticsForThisLine, computeIndent(line), sanitizeDiagnostic)
        }
        result.appendLine(line)
    }

    return result.toString()
}

private fun StringBuilder.appendDiagnostics(
    diagnostics: List<BuildProblem>,
    withIndent: Int,
    sanitizeDiagnostic: (String) -> String
) {
    val sorted = diagnostics.sortedWith(
        compareByDescending<BuildProblem> { it.level }
            .then(compareBy { it.message })
    )

    for (diagnostic in sorted) {
        append(generateIndent(withIndent))
        appendLine(diagnostic.renderAnnotation(sanitizeDiagnostic))
    }
}

private fun computeIndent(line: String): Int = line.takeWhile { it.isWhitespace() }.count()
private fun generateIndent(size: Int) = " ".repeat(size)

private fun String.trimTrailingWhitespacesAndEmptyLines(): String {
    return lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n") { it.trimEnd() }
}

private fun BuildProblem.renderAnnotation(sanitizeDiagnostic: (String) -> String): String =
    YAML_COMMENT_START + " " + DIAGNOSTIC_ANNOTATION_LB + renderWithSanitization(sanitizeDiagnostic) + DIAGNOSTIC_ANNOTATION_RB

private fun BuildProblem.renderWithSanitization(sanitizeDiagnostic: (String) -> String): String =
    sanitizeDiagnostic(render().replace("\n", " "))

const val DIAGNOSTIC_ANNOTATION_LB = "<!"
const val DIAGNOSTIC_ANNOTATION_RB = "!>"
const val YAML_COMMENT_START = "#"
val DIAGNOSTIC_REGEX = """$YAML_COMMENT_START $DIAGNOSTIC_ANNOTATION_LB[^<!>]*$DIAGNOSTIC_ANNOTATION_RB""".toRegex()
