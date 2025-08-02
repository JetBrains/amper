/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Factory to provide diagnostics on the whole AOM [Model].
 *
 * Use this factory only if you need to analyze several [AmperModule]s together.
 *
 * Otherwise, prefer [AomSingleModuleDiagnosticFactory] to have smaller scope to diagnose.
 *
 * Register instances of factory in [AomModelDiagnosticFactories].
 */
interface AomModelDiagnosticFactory {

    /**
     * Analyzes the given project [model] and reports any problems using the given [problemReporter].
     */
    fun analyze(model: Model, problemReporter: ProblemReporter)
}
