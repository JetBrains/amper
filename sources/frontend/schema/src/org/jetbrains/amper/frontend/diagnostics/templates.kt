/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitListProperties
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.scalarValue
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.pathString

object TemplateNameWithoutPostfix : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "template.name.without.postfix"
    override fun ProblemReporterContext.analyze(root: MergedTree, minimalModule: MinimalModule) =
        root.visitListProperties(Module::apply) { _, templatesRaw ->
            templatesRaw.children.map { it to it.scalarValue<TraceablePath>()?.value }.forEach { (tValue, template) ->
                if (template?.exists() == true &&
                    template.extension != "amper" &&
                    !template.pathString.endsWith(".module-template.yaml")
                ) SchemaBundle.reportBundleError(
                    tValue.trace,
                    diagnosticId,
                )
            }
        }
}

object UnresolvedTemplate : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "unresolved.template"
    override fun ProblemReporterContext.analyze(root: MergedTree, minimalModule: MinimalModule) =
        root.visitListProperties(Module::apply) { _, templatesRaw ->
            templatesRaw.children.map { it to it.scalarValue<TraceablePath>()?.value }.forEach { (tValue, template) ->
                if (template != null && !template.exists()) SchemaBundle.reportBundleError(
                    tValue.trace,
                    diagnosticId,
                    template,
                )
            }
        }
}