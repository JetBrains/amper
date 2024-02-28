/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model

/**
 * Factory to provide diagnostics on the whole AOM [Model].
 *
 * Use this factory only if you need to analyze several modules
 * ([PotatoModule][org.jetbrains.amper.frontend.PotatoModule]) together.
 *
 * Otherwise, prefer [AomSingleModuleDiagnosticFactory] to have smaller scope to diagnose.
 *
 * Register instances of factory in [AomModelDiagnosticFactories].
 */
interface AomModelDiagnosticFactory {
    val diagnosticId: BuildProblemId

    context(ProblemReporterContext)
    fun Model.analyze()
}