/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.FileBuildProblemSource
import org.jetbrains.amper.core.messages.FileWithRangesBuildProblemSource
import org.jetbrains.amper.core.messages.MultipleLocationsBuildProblemSource
import java.nio.file.Path

private const val DIAGNOSTIC_START_LB = "<!"
private const val DIAGNOSTIC_START_RB = "!>"
private const val DIAGNOSTIC_END = "<!>"

// escaped constants to include them literally in the regex
private val R_DIAG_START_LB = Regex.escape(DIAGNOSTIC_START_LB)
private val R_DIAG_START_RB = Regex.escape(DIAGNOSTIC_START_RB)
private val R_DIAG_END = Regex.escape(DIAGNOSTIC_END)

private val DIAGNOSTIC_REGEX =
    """($R_DIAG_START_LB((?!$R_DIAG_START_RB).)*$R_DIAG_START_RB)+(?<code>((?!$R_DIAG_END).)*)($R_DIAG_END)+"""
        .toRegex(RegexOption.DOT_MATCHES_ALL)

fun PsiFile.removeDiagnosticAnnotations(): PsiFile {
    val newFile = copy() as PsiFile
    val newText = newFile.text.replace(DIAGNOSTIC_REGEX) { result ->
        result.groups["code"]?.value ?: error("Diagnostic regex matched but the 'code' capturing group is missing")
    }
    val document = newFile.viewProvider.document
    document.setText(newText)
    return newFile
}

fun annotateTextWithDiagnostics(
    origin: Path,
    intoText: String,
    diagnostics: List<BuildProblem>,
    sanitizeDiagnostic: (String) -> String
): String {
    @Suppress("RemoveExplicitTypeArguments") // Making compiler happy
    val locationToDiagnosticMap = buildMap<BuildProblemSource, BuildProblem> {
        diagnostics.forEach { diagnostic ->
            when (val source = diagnostic.source) {
                is MultipleLocationsBuildProblemSource -> source.sources.forEach { put(it, diagnostic) }
                else -> put(source, diagnostic)
            }
        }
    }.toList()

    val (diagnosticsWithOffsets, diagnosticsWithoutOffsets) = locationToDiagnosticMap.partition { (source, _) ->
        source is FileWithRangesBuildProblemSource
    }

    return buildString {
        appendFileDiagnostics(diagnosticsWithoutOffsets.filter { (source, _) ->
            source is FileBuildProblemSource && source.file == origin
        }.map { it.second }, sanitizeDiagnostic)
        appendFileTextDecoratedWithDiagnostics(origin, intoText, diagnosticsWithOffsets, sanitizeDiagnostic)
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
        append(DIAGNOSTIC_START_LB)
        append(diagnostic.renderWithSanitization(sanitizeDiagnostic))
        append(DIAGNOSTIC_START_RB)
        appendLine(DIAGNOSTIC_END)
    }
}

private fun BuildProblem.renderWithSanitization(sanitizeDiagnostic: (String) -> String): String =
    sanitizeDiagnostic("[$level] $message".replace("\n", " "))

private data class DiagnosticPoint(val offset: Int, val isStart: Boolean, val problem: BuildProblem)

private val diagnosticComparator =
    compareBy<Pair<BuildProblemSource, BuildProblem>> { (it.first as FileWithRangesBuildProblemSource).offsetRange.first }
        .thenBy { (it.first as FileWithRangesBuildProblemSource).offsetRange.last }
        .thenBy { it.second.message }

private fun StringBuilder.appendFileTextDecoratedWithDiagnostics(
    origin: Path,
    clearedText: String,
    diagnostics: List<Pair<BuildProblemSource, BuildProblem>>,
    sanitizeDiagnostic: (String) -> String,
) {
    val sortedDiagnostics = diagnostics.sortedWith(diagnosticComparator)

    val diagnosticPoints: List<DiagnosticPoint> = sortedDiagnostics.flatMap { (source, diagnostic) ->
        source as FileWithRangesBuildProblemSource
        if (source.file == origin) {
            val startOffset = source.offsetRange.first
            val endOffset = source.offsetRange.last
            listOf(DiagnosticPoint(startOffset, true, diagnostic), DiagnosticPoint(endOffset, false, diagnostic))
        } else emptyList()
    }.sortedBy { it.offset }

    var lastOffset = 0
    diagnosticPoints.forEach { (offset, isStart, problem) ->
        append(clearedText, lastOffset, offset)
        lastOffset = offset
        if (isStart) {
            append(DIAGNOSTIC_START_LB)
            append(problem.renderWithSanitization(sanitizeDiagnostic))
            append(DIAGNOSTIC_START_RB)
        } else {
            append(DIAGNOSTIC_END)
        }
    }
    if (lastOffset < clearedText.length) {
        append(clearedText, lastOffset, clearedText.length)
    }
}
