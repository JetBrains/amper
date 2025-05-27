/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.contexts.PlatformCtx
import org.jetbrains.amper.frontend.diagnostics.helpers.extractKeyElement
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.OwnedTree
import org.jetbrains.amper.frontend.tree.visitValues


object UnknownQualifiers : OwnedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "product.unknown.qualifiers"
    private val knownPlatforms = Platform.values.map { it.schemaValue }
    override fun ProblemReporterContext.analyze(root: OwnedTree, minimalModule: MinimalModule) {
        val knownAliases = minimalModule.aliases?.keys.orEmpty()
        val knownModifiers = knownAliases + knownPlatforms
        root.visitValues { value ->
            value.contexts
                .filterIsInstance<PlatformCtx>().filter { it.trace != null }
                .filter { it.value !in knownModifiers }
                .forEach {
                    SchemaBundle.reportBundleError(
                        node = it.trace?.extractPsiElementOrNull()?.extractKeyElement()
                            ?: return@forEach, // Skip for unknown places.
                        messageKey = "product.unknown.qualifier",
                        it.value,
                    )
                }
        }
    }
}