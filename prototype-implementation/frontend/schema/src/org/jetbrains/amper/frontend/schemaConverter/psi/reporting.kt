/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.reportError
import org.jetbrains.yaml.psi.YAMLPsiElement

/**
 * Assert that node has specified type and then execute provided block,
 * reporting an error if the type is invalid.
 */
context(ProblemReporterContext)
inline fun <reified NodeT, T> YAMLPsiElement.assertNodeType(
    fieldName: String,
    report: Boolean = true,
    block: NodeT.() -> T
): T? {
    // TODO Replace by bundle.
    if (this !is NodeT && report) return reportError("[$fieldName] field has wrong type: " +
            "It is ${this::class.simpleName}, but was expected to be ${NodeT::class.simpleName}", node = this)
    if (this !is NodeT) return null
    return this.block()
}