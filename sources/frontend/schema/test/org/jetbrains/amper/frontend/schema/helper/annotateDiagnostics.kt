/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.BuildProblem

private const val DIAGNOSTIC_ANNOTATION_LB = "<!"
private const val DIAGNOSTIC_ANNOTATION_RB = "!>"
private const val DIAGNOSTIC_END = "<!>"
private val DIAGNOSTIC_REGEX = """$DIAGNOSTIC_ANNOTATION_LB[^<!>]*$DIAGNOSTIC_ANNOTATION_RB(.+?)$DIAGNOSTIC_END""".toRegex()

fun PsiFile.removeDiagnosticAnnotations(): PsiFile {
    val newFile = copy() as PsiFile
    val newText = newFile.text.replace(DIAGNOSTIC_REGEX) { result -> result.groupValues[1] }
    val document = newFile.viewProvider.document
    document.setText(newText)
    return newFile
}

fun annotateTextWithDiagnostics(
    intoText: String,
    diagnostics: List<BuildProblem>,
    sanitizeDiagnostic: (String) -> String
): String {
    val (diagnosticsWithOffsets, diagnosticsWithoutOffsets) = diagnostics.partition { it.source?.range != null }

    return buildString {
        appendFileDiagnostics(diagnosticsWithoutOffsets, sanitizeDiagnostic)
        appendTextDecoratedWithDiagnostics(intoText, diagnosticsWithOffsets, sanitizeDiagnostic)
    }
}

private fun StringBuilder.appendFileDiagnostics(
    diagnostics: List<BuildProblem>,
    sanitizeDiagnostic: (String) -> String,
) {
    val sortedDiagnostics = diagnostics.sortedWith(
        compareByDescending<BuildProblem> { it.level }
            .then(compareBy { it.message })
    )

    for (diagnostic in sortedDiagnostics) {
        append(DIAGNOSTIC_ANNOTATION_LB)
        append(diagnostic.renderWithSanitization(sanitizeDiagnostic))
        append(DIAGNOSTIC_ANNOTATION_RB)
        appendLine(DIAGNOSTIC_END)
    }
}

private fun BuildProblem.renderWithSanitization(sanitizeDiagnostic: (String) -> String): String =
    sanitizeDiagnostic("[$level] $message".replace("\n", " "))

private data class DiagnosticPoint(val offset: Int, val isStart: Boolean, val problem: BuildProblem)

private fun StringBuilder.appendTextDecoratedWithDiagnostics(
    clearedText: String,
    diagnostics: List<BuildProblem>,
    sanitizeDiagnostic: (String) -> String,
) {
    val sortedDiagnostics = diagnostics.sortedWith(
        compareBy<BuildProblem> { it.source?.offsetRange?.start }
            .then(compareBy { it.source?.offsetRange?.last })
    )

    val diagnosticPoints: List<DiagnosticPoint> = sortedDiagnostics.flatMap { diagnostic ->
        val startOffset = diagnostic.source?.offsetRange?.start ?: 0
        val endOffset = diagnostic.source?.offsetRange?.last ?: 0
        listOf(DiagnosticPoint(startOffset, true, diagnostic), DiagnosticPoint(endOffset, false, diagnostic))
    }.sortedBy { it.offset }

    var lastOffset = 0
    diagnosticPoints.forEach { (offset, isStart, problem) ->
        append(clearedText, lastOffset, offset)
        lastOffset = offset
        if (isStart) {
            append(DIAGNOSTIC_ANNOTATION_LB)
            append(problem.renderWithSanitization(sanitizeDiagnostic))
            append(DIAGNOSTIC_ANNOTATION_RB)
        } else {
            append(DIAGNOSTIC_END)
        }
    }
    if (lastOffset < clearedText.length) {
        append(clearedText, lastOffset, clearedText.length)
    }
}
