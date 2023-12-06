/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.yaml.snakeyaml.nodes.Node


/**
 * Reports an error and conveniently returns null.
 */
context(ProblemReporterContext)
fun Node.reportError(message: String): Nothing? {
    problemReporter.reportMessage(BuildProblem(message, Level.Error, line = this.startMark.line))
    return null
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
    if (this !is NodeT && report) return reportError("[$fieldName] field has wrong type: " +
            "It is ${this::class.simpleName}, but was expected to be ${NodeT::class.simpleName}")
    if (this !is NodeT) return null
    return this.block()
}