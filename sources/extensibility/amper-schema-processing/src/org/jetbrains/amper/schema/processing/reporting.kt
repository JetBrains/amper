/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import org.jetbrains.amper.plugins.schema.model.SourceLocation
import java.text.MessageFormat
import java.util.*

internal interface DiagnosticsReporter {
    fun report(diagnostic: PluginDataResponse.Diagnostic)
}

context(reporter: DiagnosticsReporter)
internal fun reportError(
    where: PsiElement,
    messageKey: String,
    vararg values: Any?,
) = report(where.getSourceLocation(), messageKey, values = values, kind = PluginDataResponse.DiagnosticKind.ErrorGeneric)

context(reporter: DiagnosticsReporter)
internal fun report(
    where: PsiElement,
    messageKey: String,
    vararg values: Any?,
    kind: PluginDataResponse.DiagnosticKind,
) = report(where = where.getSourceLocation(), messageKey = messageKey, values = values, kind = kind)

context(reporter: DiagnosticsReporter)
internal fun report(
    where: SourceLocation,
    messageKey: String,
    vararg values: Any?,
    kind: PluginDataResponse.DiagnosticKind,
) {
    val specificMessage = MessageFormat(SchemaProcessorBundle.getString(messageKey)).format(values)
    reporter.report(PluginDataResponse.Diagnostic(
        location = where,
        message = SchemaMessageFormat.format(arrayOf(specificMessage)),
        diagnosticId = messageKey,
        kind = kind,
    ))
}

internal fun PsiElement.getSourceLocation(): SourceLocation {
    return SourceLocation(
        path = containingFile.virtualFile.toNioPath(),
        textRange = textRange.let { it.startOffset..it.endOffset },
    )
}

private val SchemaProcessorBundle: ResourceBundle = ResourceBundle.getBundle("messages.SchemaProcessorBundle")
private val SchemaMessageFormat = MessageFormat(SchemaProcessorBundle.getString("0.schema.message.format"))