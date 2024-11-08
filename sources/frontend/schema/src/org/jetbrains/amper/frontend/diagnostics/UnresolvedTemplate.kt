/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.Module
import kotlin.io.path.exists

object UnresolvedTemplate : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "unresolved.template"

    context(ProblemReporterContext)
    override fun Module.analyze() {
        this::apply.unsafe?.let {
            for (path in it) {
                if (!path.value.exists()) {
                    path.trace?.extractPsiElementOrNull()?.let {
                        problemReporter.reportMessage(
                            object : PsiBuildProblem(Level.Error) {
                                override val element: PsiElement = it
                                override val buildProblemId = "unresolved.template"
                                override val message = SchemaBundle.message("unresolved.template", path.value.toString())
                            }
                        )
                    }
                }
            }
        }
    }
}