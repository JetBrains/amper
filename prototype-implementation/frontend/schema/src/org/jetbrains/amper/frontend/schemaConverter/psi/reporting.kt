/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.yaml.psi.YAMLPsiElement


/**
 * Reports an error and conveniently returns null.
 */
context(ProblemReporterContext)
fun YAMLPsiElement.reportError(message: String): Nothing? {
    // TODO (AB) : How to resolve (line;column) coordinates by PSI node textOffset?
    problemReporter.reportMessage(BuildProblem(message, Level.Error, line = -1))
    return null
}

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
            "It is ${this::class.simpleName}, but was expected to be ${NodeT::class.simpleName}")
    if (this !is NodeT) return null
    return this.block()
}