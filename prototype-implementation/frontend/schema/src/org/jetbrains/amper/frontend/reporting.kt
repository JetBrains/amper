/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.yaml.snakeyaml.nodes.Node


object SchemaBundle : MessageBundle("messages.FrontendSchemaBundle")

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    value: ValueBase<*>,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = when(val trace = value.trace) {
    is Node -> reportError(message(messageKey, *arguments), level, trace)
    is YAMLPsiElement -> reportError(message(messageKey, *arguments), level, trace)
    else -> reportError(message(messageKey, *arguments), level, null as YAMLPsiElement?)
}

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    node: Node?,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, node)

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    node: YAMLPsiElement?,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, node)

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, null as Node?)

context(ProblemReporterContext)
fun reportError(
    message: String,
    level: Level = Level.Error,
    node: Node? = null
): Nothing? {
    problemReporter.reportMessage(BuildProblem(message, level, line = node?.startMark?.line?.let { it + 1 }))
    return null
}

context(ProblemReporterContext)
fun reportError(
    message: String,
    level: Level = Level.Error,
    node: YAMLPsiElement? = null
): Nothing? {
    val lineAndColumn = node?.let { getLineAndColumnInPsiFile(it, it.textRange) }
    problemReporter.reportMessage(BuildProblem(message, level, line = lineAndColumn?.line?.let { it+1 }, column = lineAndColumn?.column))
    return null
}

fun getLineAndColumnInPsiFile(node: YAMLPsiElement, range: TextRange): LineAndColumn {
    val document: Document = node.containingFile.viewProvider.document
    return offsetToLineAndColumn(document, range.startOffset)
}

fun offsetToLineAndColumn(
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

/**
 * Assert that node has specified type and then execute provided block,
 * reporting an error if the type is invalid.
 */
context(ProblemReporterContext)
inline fun <reified NodeT, T> Node.assertNodeType(
    fieldName: String,
    report: Boolean = true,
    block: NodeT.() -> T
): T? {
    // TODO Replace by bundle.
    if (this !is NodeT && report) return reportError(
        "[$fieldName] field has wrong type: " +
                "It is ${this::class.simpleName}, but was expected to be ${NodeT::class.simpleName}",
        Level.Error,
        this,
    )
    if (this !is NodeT) return null
    return this.block()
}

class LineAndColumn(val line: Int, val column: Int, val lineContent: String?) {
    // NOTE: This method is used for presenting positions to the user
    override fun toString(): String {
        if (line < 0) {
            return "(offset: $column line unknown)"
        }
        return "($line,$column)"
    }

    companion object {
        val NONE: LineAndColumn = LineAndColumn(-1, -1, null)
    }
}

class LineAndColumnRange(val start: LineAndColumn, val end: LineAndColumn) {
    // NOTE: This method is used for presenting positions to the user
    override fun toString(): String {
        if (start.line == end.line) {
            return "(" + start.line + "," + start.column + "-" + end.column + ")"
        }

        return "$start - $end"
    }

    companion object {
        val NONE: LineAndColumnRange = LineAndColumnRange(LineAndColumn.NONE, LineAndColumn.NONE)
    }
}