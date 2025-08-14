/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.Platform.COMMON
import org.jetbrains.amper.frontend.Platform.Companion.naturalHierarchy
import org.jetbrains.amper.frontend.Platform.Companion.naturalHierarchyExt
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.contexts.platformCtxs
import org.jetbrains.amper.frontend.contexts.unwrapAliases
import org.jetbrains.amper.frontend.diagnostics.helpers.extractKeyElement
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.OwnedTree
import org.jetbrains.amper.frontend.tree.visitMapLikeValues
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * [naturalHierarchy] with [COMMON] and leaves included.
 */
private val naturalHierarchyExtStr = naturalHierarchyExt.mapKeys { it.key.schemaValue }

object IncorrectSettingsLocation : OwnedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "settings.incorrect.section"

    override fun analyze(root: OwnedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) =
        root.visitMapLikeValues { tree ->
            tree.children.forEach { PropertyCheck(problemReporter, minimalModule, prop = it).doCheck() }
        }

    private class PropertyCheck(
        val problemReporter: ProblemReporter,
        val minimalModule: MinimalModule,
        val prop: MapLikeValue.Property<*>,
    ) {
        fun doCheck() = if (prop.value.trace !is DefaultTrace) {
            platformAgnostic()
            platformSpecific()
            productSpecific()
            gradleSpecific()
        } else Unit

        fun platformAgnostic() {
            if (prop.pType?.isPlatformAgnostic == true && prop.contexts.platformCtxs().isNotEmpty()) {
                problemReporter.reportMessage(
                    IncorrectSettingsSection(
                        trace = prop.value.trace,
                        messageKey = "settings.unexpected.platform",
                        level = Level.Error,
                    )
                )
            }
        }

        private fun platformSpecific() = prop.pType?.specificToPlatforms?.takeIf { it.isNotEmpty() }?.let { platforms ->
            val platformsAndAliases =
                minimalModule.unwrapAliases + naturalHierarchyExtStr - COMMON.schemaValue
            val propPlatforms = prop.contexts.platformCtxs().flatMap { platformsAndAliases[it.value] ?: emptyList() }.toSet()
            // Here we are considering "empty platforms" as all declared ones.
            val effectivePlatforms = propPlatforms.ifEmpty { minimalModule.product.platforms.leaves }
            if (platforms.leaves.intersect(effectivePlatforms).isEmpty())
                problemReporter.reportMessage(
                    IncorrectSettingsSection(
                        trace = prop.value.trace,
                        messageKey = "settings.incorrect.platform",
                        effectivePlatforms.joinToString { it.schemaValue },
                        platforms.joinToString { it.schemaValue },
                        level = Level.Warning,
                    )
                )
        }

        private fun productSpecific() = prop.pType?.specificToProducts?.takeIf { it.isNotEmpty() }?.let { productTypes ->
            val usedProductType = minimalModule.product.type
            if (!productTypes.contains(usedProductType)) problemReporter.reportMessage(
                IncorrectSettingsSection(
                    trace = prop.value.trace,
                    messageKey = "settings.incorrect.product.type",
                    usedProductType,
                    productTypes.joinToString { it.value },
                    level = Level.Warning,
                )
            )
        }

        private fun gradleSpecific() = prop.pType?.specificToGradleMessage?.let { message ->
            problemReporter.reportMessage(
                IncorrectSettingsSection(
                    trace = prop.value.trace,
                    messageKey = "gradle.specific.unsupported",
                    message,
                    level = Level.Warning,
                    buildProblemType = BuildProblemType.ObsoleteDeclaration,
                )
            )
        }
    }
}

class IncorrectSettingsSection internal constructor(
    @UsedInIdePlugin
    val trace: Trace,
    messageKey: String,
    vararg values: Any?,
    level: Level,
    buildProblemType: BuildProblemType = BuildProblemType.Generic,
) : PsiBuildProblem(level, buildProblemType) {
    override val message = SchemaBundle.message(messageKey, *values)
    override val buildProblemId: BuildProblemId = IncorrectSettingsLocation.diagnosticId

    // highlight only property keys
    override val element: PsiElement = trace.extractPsiElement().extractKeyElement()
}