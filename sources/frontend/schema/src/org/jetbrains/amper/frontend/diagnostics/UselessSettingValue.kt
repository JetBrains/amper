/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.collectScalarPropertiesWithOwners
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.valueEqualsTo
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

private const val DiagnosticId = "setting.value.overrides.nothing"

// FIXME: This diagnostic is not enabled.
//  It relies on the fact, that an arbitrary configuration *sub*tree can be correctly refined.
//  That was the property of so-called "merged" trees, that are no longer built by default.
//  So this diagnostic must do its own preprocessing
class UselessSettingValue(
    private val refiner: TreeRefiner,
) : TreeDiagnostic {
    companion object {
        const val diagnosticId = DiagnosticId
    }
    override val diagnosticId = DiagnosticId

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        // TODO There an optimization can be made.
        //  Here we can group by not by key name, but by key path.
        val groupedScalars = root.collectScalarPropertiesWithOwners().groupBy { it.second.key }.map { (_, it) -> it }
        val hasPotentialOverrides = groupedScalars.filter { it.size > 1 }
        hasPotentialOverrides.forEach { group ->
            // TODO There an optimization can be made.
            //  We may not refine for all used contexts - only for most specific ones.
            group.forEach { (owner, scalarProp) ->
                val refined = context(problemReporter) {
                    refiner.refineTree(owner, scalarProp.value.contexts)
                }

                // Since there is at least one value assignment,
                // we can safely assume that after refinement it is exactly single.
                val refinedProp = refined[scalarProp.key] ?: return@forEach
                refinedProp.trace.precedingValue
                    ?.takeIf { (it as? ScalarNode) valueEqualsTo (refinedProp as? ScalarNode) }
                    ?.let {
                        problemReporter.reportMessage(
                            UselessSetting(refinedProp.trace, it.trace.asSafely<PsiTrace>() ?: return@forEach),
                        )
                    }
            }
        }
    }
}

private class UselessSetting(
    trace: Trace,
    private val precedingTrace: PsiTrace,
) : PsiBuildProblem(Level.WeakWarning, BuildProblemType.RedundantDeclaration) {
    override val element: PsiElement = trace.extractPsiElement()
    override val buildProblemId = DiagnosticId
    override val message = SchemaBundle.message(
        messageKey = "setting.value.is.same.as.base",
        formatLocation(),
    )

    private fun formatLocation(): String {
        val file = precedingTrace.psiElement.containingFile!!
        return if (file.name != "module.yaml" && file.name != "module.amper") file.name
        else file.parent!!.name
    }
}