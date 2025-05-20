/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.annotations.Nls
import kotlin.reflect.full.findAnnotation

object GradleSpecificUnsupportedFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId get() = "gradle.specific.unsupported"

    context(ProblemReporterContext)
    override fun AmperModule.analyze() {
        val visitor = Visitor(problemReporter)
        for (node in arrayOf(origin)) {
            visitor.visit(node)
        }
    }

    private class Visitor(
        val reporter: ProblemReporter,
    ) : SchemaValuesVisitor() {
        override fun visitValue(it: ValueBase<*>) {
            super.visitValue(it)

            if (it.withoutDefault == null) return
            val gradleSpecific = it.property.findAnnotation<GradleSpecific>() ?: return

            reporter.reportMessage(object : PsiBuildProblem(level = Level.Warning) {
                override val element: PsiElement
                    get() = it.extractPsiElement()
                override val buildProblemId get() = diagnosticId
                override val message: @Nls String
                    get() = SchemaBundle.message("gradle.specific.unsupported", gradleSpecific.message)
            })
        }
    }
}
