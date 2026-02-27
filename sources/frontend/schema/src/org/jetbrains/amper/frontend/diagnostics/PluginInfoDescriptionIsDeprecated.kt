/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitEnumProperties
import org.jetbrains.amper.frontend.diagnostics.helpers.visitNullableStringProperties
import org.jetbrains.amper.frontend.diagnostics.helpers.visitStringProperties
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.plugins.MinimalPluginDeclarationSchema
import org.jetbrains.amper.frontend.plugins.PluginDeclarationSchema
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.visitNodes
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

class PluginInfoDescriptionIsDeprecated(
    override val element: PsiElement,
) : PsiBuildProblem(Level.Warning, BuildProblemType.ObsoleteDeclaration) {
    override val buildProblemId = PluginInfoDescriptionIsDeprecatedFactory.diagnosticId
    override val message = SchemaBundle.message(buildProblemId)
}

object PluginInfoDescriptionIsDeprecatedFactory : TreeDiagnostic {

    override val diagnosticId: BuildProblemId = "plugin.description.should.be.top.level"

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        @Suppress("DEPRECATION")
        root.visitNullableStringProperties(PluginDeclarationSchema::description) { prop, value ->
            val versionTraceElement = prop.trace.extractPsiElementOrNull() ?: return@visitNullableStringProperties
            if (value != null) {
                problemReporter.reportMessage(PluginInfoDescriptionIsDeprecated(element = versionTraceElement))
            }
        }
        @Suppress("DEPRECATION")
        root.visitNullableStringProperties(MinimalPluginDeclarationSchema::description) { prop, value ->
            val versionTraceElement = prop.trace.extractPsiElementOrNull() ?: return@visitNullableStringProperties
            if (value != null) {
                problemReporter.reportMessage(PluginInfoDescriptionIsDeprecated(element = versionTraceElement))
            }
        }
    }
}
