/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitListProperties
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString

object TemplateNameWithoutPostfix : TreeDiagnosticFactory {
    const val diagnosticId: BuildProblemId = "template.name.without.postfix"

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) =
        root.visitListProperties(Module::apply) { _, templatesRaw ->
            templatesRaw.children.map { it to (it as? PathNode)?.value }.forEach { (tValue, template) ->
                if (template?.exists() == true &&
                    template.extension != "amper" &&
                    !template.pathString.endsWith(".module-template.yaml")
                ) {
                    problemReporter.reportBundleError(
                        source = tValue.trace.asBuildProblemSource(),
                        diagnosticId = FrontendDiagnosticId.TemplateNameWithoutPostfix,
                        messageKey = diagnosticId,
                    )
                }
            }
        }
}
