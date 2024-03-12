/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.yaml.psi.YAMLPsiElement

/**
 * Assert that node has the specified type and then execute provided block,
 * reporting an error if the type is invalid.
 */
context(ProblemReporterContext)
inline fun <reified NodeT, T> PsiElement.assertNodeType(
    fieldName: String,
    report: Boolean = true,
    block: NodeT.() -> T
): T? {
    if (this !is NodeT && report) return SchemaBundle.reportBundleError(
        node = this,
        messageKey = "wrong.node.type",
        fieldName,
        this::class.simpleName,
        NodeT::class.simpleName,
    )
    if (this !is NodeT) return null
    return this.block()
}