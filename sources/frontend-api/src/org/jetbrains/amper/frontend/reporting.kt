/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.LineAndColumn
import org.jetbrains.amper.core.messages.LineAndColumnRange
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import kotlin.reflect.KProperty0

object SchemaBundle : MessageBundle("messages.FrontendSchemaBundle")

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    property: KProperty0<*>,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportBundleError(property.valueBase, messageKey, level = level, arguments = arguments)

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    value: ValueBase<*>?,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = when(val trace = value?.trace) {
    is PsiTrace -> reportError(message(messageKey, *arguments), level, trace.psiElement)
    else -> reportError(message(messageKey, *arguments), level, null as PsiElement?)
}

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    value: TraceableString,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = when(val trace = value.trace) {
    is PsiTrace -> reportError(message(messageKey, *arguments), level, trace.psiElement)
    else -> reportError(message(messageKey, *arguments), level, null as PsiElement?)
}

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    node: PsiElement,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, node)

context(ProblemReporterContext)
fun reportError(
    message: String,
    level: Level = Level.Error,
    node: PsiElement? = null
): Nothing? {
    problemReporter.reportMessage(BuildProblem(message, level, source = node?.let(::PsiBuildProblemSource)))
    return null
}

internal fun getLineAndColumnRangeInPsiFile(node: PsiElement): LineAndColumnRange {
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