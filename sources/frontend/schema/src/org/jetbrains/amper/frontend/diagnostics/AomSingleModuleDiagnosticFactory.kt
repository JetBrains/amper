/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule

/**
 * Factory to provide diagnostics on an AOM [AmperModule].
 *
 * Use this factory to analyze built AOM modules with propagated properties and resolved internal dependencies.
 *
 * For simpler cases where you don't need to check propagation use [IsmDiagnosticFactory].
 * For more complex cases where you need to analyze several modules at once use [AomModelDiagnosticFactory].
 *
 * Register instances of factory in [AomSingleModuleDiagnosticFactory].
 */
interface AomSingleModuleDiagnosticFactory {
    val diagnosticId: BuildProblemId

    context(ProblemReporterContext)
    fun AmperModule.analyze()
}