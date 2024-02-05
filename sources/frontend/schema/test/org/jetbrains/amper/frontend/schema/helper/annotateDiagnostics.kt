/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.render

fun PsiFile.removeDiagnosticAnnotations(): PsiFile {
    val newFile = copy() as PsiFile
    PsiTreeUtil.findChildrenOfType(this, PsiComment::class.java)
        .filter { it.text.matches(DIAGNOSTIC_REGEX) }
        .map { PsiTreeUtil.findSameElementInCopy(it, newFile) }
        .forEach { it.delete() }
    return newFile
}

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

fun String.trimTrailingWhitespacesAndEmptyLines(): String {
    return lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n") { it.trimEnd() }
}

private fun BuildProblem.renderAnnotation(sanitizeDiagnostic: (String) -> String): String =
    YAML_COMMENT_START + " " + DIAGNOSTIC_ANNOTATION_LB + renderWithSanitization(sanitizeDiagnostic) + DIAGNOSTIC_ANNOTATION_RB

private fun BuildProblem.renderWithSanitization(sanitizeDiagnostic: (String) -> String): String =
    sanitizeDiagnostic(render().replace("\n", " "))

private const val DIAGNOSTIC_ANNOTATION_LB = "<!"
private const val DIAGNOSTIC_ANNOTATION_RB = "!>"
private const val YAML_COMMENT_START = "#"
private val DIAGNOSTIC_REGEX = """$YAML_COMMENT_START $DIAGNOSTIC_ANNOTATION_LB[^<!>]*$DIAGNOSTIC_ANNOTATION_RB""".toRegex()
