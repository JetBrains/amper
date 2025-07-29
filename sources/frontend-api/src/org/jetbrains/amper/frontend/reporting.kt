/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.LineAndColumn
import org.jetbrains.amper.core.messages.LineAndColumnRange
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.valueBaseOrNull
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
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
) {
    reportMessage(
        BuildProblemImpl(
            buildProblemId = buildProblemId,
            source = source,
            message = bundle.message(messageKey, *arguments),
            level = level,
        )
    )
}

fun KProperty0<*>.asBuildProblemSource(): BuildProblemSource = valueBaseOrNull?.trace?.asBuildProblemSource()
    ?: error("Cannot get BuildProblemSource for property $name ($this) because it's not a value delegate")

fun Traceable.asBuildProblemSource(): BuildProblemSource =
    (trace ?: error("Cannot get BuildProblemSource for this Traceable ($this) because it has no trace"))
        .asBuildProblemSource()

@OptIn(NonIdealDiagnostic::class)
fun Trace.asBuildProblemSource(): BuildProblemSource = extractPsiElementOrNull()?.asBuildProblemSource()
    ?: GlobalBuildProblemSource

fun PsiElement.asBuildProblemSource(): BuildProblemSource = PsiBuildProblemSource(this)

fun getLineAndColumnRangeInPsiFile(node: PsiElement): LineAndColumnRange {
    val document: Document = node.containingFile.viewProvider.document
    val textRange = node.textRange
    return LineAndColumnRange(
        offsetToLineAndColumn(document, textRange.startOffset),
        offsetToLineAndColumn(document, textRange.endOffset),
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
