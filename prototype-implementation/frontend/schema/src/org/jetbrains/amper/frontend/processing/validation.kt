/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.ValidationVisitor
import org.jetbrains.amper.frontend.schema.Module

context(ProblemReporterContext)
fun Module.validateSchema() = apply {
    ValidationVisitor().visit(this)
}