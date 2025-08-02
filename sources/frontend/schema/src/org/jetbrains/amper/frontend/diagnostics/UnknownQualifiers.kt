/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.diagnostics.helpers.extractKeyElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.OwnedTree
import org.jetbrains.amper.frontend.tree.visitValues
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.ProblemReporter

object UnknownQualifiers : OwnedTreeDiagnostic {

    override val diagnosticId: BuildProblemId = "product.unknown.qualifiers"

    private val knownPlatforms = Platform.values.map { it.schemaValue }

    override fun analyze(root: OwnedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        val knownAliases = minimalModule.aliases?.keys.orEmpty()
        val knownModifiers = knownAliases + knownPlatforms
        root.visitValues { value ->
            value.contexts
                .filterIsInstance<PlatformCtx>().filter { it.trace != null }
                .filter { it.value !in knownModifiers }
                .forEach {
                    problemReporter.reportBundleError(
                        source = it.trace?.extractPsiElementOrNull()?.extractKeyElement()?.asBuildProblemSource()
                            ?: return@forEach, // Skip for unknown places.
                        messageKey = "product.unknown.qualifier",
                        it.value,
                    )
                }
        }
    }
}