/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template

context(ProblemReporterContext)
fun Module.validateSchema() = apply {
    ValidationVisitor().visit(this)
}

context(ProblemReporterContext)
fun Template.validateSchema() = apply {
    ValidationVisitor().visit(this)
}

/**
 * Visitor that invokes all validation handlers.
 */
context(ProblemReporterContext)
class ValidationVisitor : SchemaValuesVisitor() {
    override fun visitNode(it: SchemaNode) {
        with(it) { validate() }
        super.visitNode(it)
    }
}
