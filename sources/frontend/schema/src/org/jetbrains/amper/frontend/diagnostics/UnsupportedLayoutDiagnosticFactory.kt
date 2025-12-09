/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitScalarProperties
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AmperLayout
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

class UnsupportedLayoutBuildProblem(override val element: PsiElement): PsiBuildProblem(Level.Error, BuildProblemType.InconsistentConfiguration) {
    override val buildProblemId: BuildProblemId = UnsupportedLayoutDiagnosticFactory.diagnosticId
    override val message: @Nls String = SchemaBundle.message(buildProblemId)
}

object UnsupportedLayoutDiagnosticFactory: TreeDiagnostic {
    override val diagnosticId: BuildProblemId = "module.layout.unsupported"

    override fun analyze(
        root: TreeNode,
        minimalModule: MinimalModule,
        problemReporter: ProblemReporter,
    ) {
        root.visitScalarProperties<Module, AmperLayout>(Module::layout) { prop, layout ->
            if (layout == AmperLayout.MAVEN_LIKE && minimalModule.product.type !in setOf(ProductType.JVM_APP, ProductType.JVM_LIB)) {
                val element = prop.value.trace.extractPsiElementOrNull() ?: return@visitScalarProperties
                problemReporter.reportMessage(UnsupportedLayoutBuildProblem(element))
            }
        }
    }
}
