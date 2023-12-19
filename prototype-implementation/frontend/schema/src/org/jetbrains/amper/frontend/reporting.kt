/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.ValueBase
import org.yaml.snakeyaml.nodes.Node


object SchemaBundle : MessageBundle("messages.FrontendSchemaBundle")

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    value: ValueBase<*>,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, value.trace as? Node)

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    node: Node?,
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level, node)

context(ProblemReporterContext)
fun MessageBundle.reportBundleError(
    messageKey: String,
    vararg arguments: Any,
    level: Level = Level.Error,
): Nothing? = reportError(message(messageKey, *arguments), level)

context(ProblemReporterContext)
fun reportError(
    message: String,
    level: Level = Level.Error,
    node: Node? = null
): Nothing? {
    problemReporter.reportMessage(BuildProblem(message, level, line = node?.startMark?.line?.let { it + 1 }))
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
    if (this !is NodeT && report) return reportError(
        "[$fieldName] field has wrong type: " +
                "It is ${this::class.simpleName}, but was expected to be ${NodeT::class.simpleName}",
        Level.Error,
        this,
    )
    if (this !is NodeT) return null
    return this.block()
}