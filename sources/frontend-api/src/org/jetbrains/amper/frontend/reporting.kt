/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.problems.reporting.BuildProblemImpl
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.LineAndColumn
import org.jetbrains.amper.problems.reporting.LineAndColumnRange
import org.jetbrains.amper.problems.reporting.MessageBundle
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.KProperty0

object SchemaBundle : MessageBundle("messages.SchemaBundle")

/**
 * Reports a problem using a localized message from the given [bundle] with the given [messageKey] and [arguments].
 * It will be reported at the given [source] location.
 */
fun ProblemReporter.reportBundleError(
    source: BuildProblemSource,
    messageKey: String,
    vararg arguments: Any?,
    bundle: MessageBundle = SchemaBundle,
    buildProblemId: String = messageKey,
    level: Level = Level.Error,
    problemType: BuildProblemType = BuildProblemType.Generic,
) {
    reportMessage(
        BuildProblemImpl(
            buildProblemId = buildProblemId,
            source = source,
            message = bundle.message(messageKey, *arguments),
            level = level,
            type = problemType,
        )
    )
}

fun KProperty0<*>.asBuildProblemSource(): BuildProblemSource = schemaDelegate.trace.asBuildProblemSource()

fun Traceable.asBuildProblemSource(): BuildProblemSource = trace.asBuildProblemSource()

@OptIn(NonIdealDiagnostic::class)
fun Trace.asBuildProblemSource(): BuildProblemSource = extractPsiElementOrNull()?.asBuildProblemSource()
    ?: GlobalBuildProblemSource

fun PsiElement.asBuildProblemSource(): PsiBuildProblemSource = PsiBuildProblemSource(this)

fun getLineAndColumnRangeInPsiFile(node: PsiElement): LineAndColumnRange {
    val document: Document = node.containingFile.viewProvider.document
    val textRange = node.textRange
    return LineAndColumnRange(
        offsetToLineAndColumn(document, textRange.startOffset),
        offsetToLineAndColumn(document, textRange.endOffset),
    )
}

fun getLineAndColumnRangeInDocument(document: Document, range: IntRange): LineAndColumnRange {
    return LineAndColumnRange(
        offsetToLineAndColumn(document, range.first),
        offsetToLineAndColumn(document, range.last),
    )
}

private fun offsetToLineAndColumn(
    document: Document?,
    offset: Int
): LineAndColumn {
    if (document == null || document.textLength == 0) {
        return LineAndColumn(-1, offset, null)
    }

    val lineNumber = document.getLineNumber(offset)
    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val column = offset - lineStartOffset

    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val lineContent = document.charsSequence.subSequence(lineStartOffset, lineEndOffset)

    return LineAndColumn(
        lineNumber + 1,
        column + 1,
        lineContent.toString()
    )
}
