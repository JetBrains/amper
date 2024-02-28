/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule

interface AomSingleModuleDiagnosticFactory {
    val diagnosticId: BuildProblemId

    context(ProblemReporterContext)
    fun PotatoModule.analyze()
}