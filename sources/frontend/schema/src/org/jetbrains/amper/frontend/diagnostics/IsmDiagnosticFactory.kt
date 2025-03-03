/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Module

/**
 * Factory for providing diagnostics on an ISM [Module].
 *
 * Note that if you need to analyze several properties at once, it's better to use [AomSingleModuleDiagnosticFactory]
 * as the property values might propagate into the fragments after AOM is built.
 *
 * Thus, use this factory for non-scoped properties or diagnostics that are performed only over a single property.
 *
 * Register instances of the factory in [IsmDiagnosticFactories].
 */
interface IsmDiagnosticFactory {
    val diagnosticId: BuildProblemId

    context(ProblemReporterContext)
    fun Module.analyze(): Unit?
}
