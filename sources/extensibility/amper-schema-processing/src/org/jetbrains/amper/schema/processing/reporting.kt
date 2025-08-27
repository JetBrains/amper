/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse
import java.text.MessageFormat
import java.util.*

internal interface DiagnosticsReporter {
    fun report(
        where: PsiElement,
        message: String,
        diagnosticId: String,
        kind: PluginDataResponse.DiagnosticKind,
    )
}

context(reporter: DiagnosticsReporter)
internal fun reportError(
    where: PsiElement,
    messageKey: String,
    vararg values: Any?,
) = report(where, messageKey, values = values, kind = PluginDataResponse.DiagnosticKind.ErrorGeneric)

context(reporter: DiagnosticsReporter)
internal fun report(
    where: PsiElement,
    messageKey: String,
    vararg values: Any?,
    kind: PluginDataResponse.DiagnosticKind,
) {
    val specificMessage = MessageFormat(SchemaProcessorBundle.getString(messageKey)).format(values)
    reporter.report(
        where = where,
        message = SchemaMessageFormat.format(arrayOf(specificMessage)),
        diagnosticId = messageKey,
        kind = kind,
    )
}

private val SchemaProcessorBundle: ResourceBundle = ResourceBundle.getBundle("messages.SchemaProcessorBundle")
private val SchemaMessageFormat = MessageFormat(SchemaProcessorBundle.getString("0.schema.message.format"))