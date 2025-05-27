/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitObjects
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Repository
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.isDefault
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.frontend.tree.scalarValueOr


object MavenLocalResolutionUnsupported : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "maven.local.resolution.not.supported"
    override fun ProblemReporterContext.analyze(root: MergedTree, minimalModule: MinimalModule) =
        root.visitObjects<Repository> { repo ->
            val isLocal = repo[Repository::url].any { it.value.scalarValue<String>() == SpecialMavenLocalUrl }
            val resolveValues = repo[Repository::resolve].map { it.value }
            // Handle single value separately to honor possible non-overridden default value.
            val isResolve = resolveValues.singleOrNull()?.let { it.scalarValueOr<Boolean> { false } } 
                ?: resolveValues.any { !it.isDefault && it.scalarValueOr<Boolean> { false } }
            if (isLocal && isResolve) SchemaBundle.reportBundleError(
                trace = repo.trace,
                messageKey = "maven.local.resolution.not.supported",
                level = Level.Warning,
            )
        }
}
