/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import java.text.MessageFormat
import java.util.*

internal interface ErrorReporter {
    fun reportError(where: PsiElement, message: String, diagnosticId: String)
}

context(reporter: ErrorReporter)
internal fun reportError(where: PsiElement, messageKey: String, vararg values: Any?) {
    reporter.reportError(
        where = where,
        message = MessageFormat(SchemaProcessorBundle.getString(messageKey)).format(values),
        diagnosticId = messageKey,
    )
}

private val SchemaProcessorBundle: ResourceBundle = ResourceBundle.getBundle("messages.SchemaProcessorBundle")