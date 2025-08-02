/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitMapLikeProperties
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.asList
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.ProblemReporter

object AliasesDontUseUndeclaredPlatform : MergedTreeDiagnostic {

    override val diagnosticId: BuildProblemId = "validation.alias.use.undeclared.platform"

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        val declaredPlatforms = minimalModule.product::platforms.unsafe?.leaves ?: return
        root.visitMapLikeProperties<Module>(Module::aliases) { _, aliasesRaw ->
            aliasesRaw.children.flatMap { it.value.asList?.children.orEmpty() }.forEach { it ->
                val platform = it.scalarValue<TraceableEnum<Platform>>()?.value ?: return@forEach
                if (platform !in declaredPlatforms) {
                    problemReporter.reportBundleError(
                        source = it.trace.asBuildProblemSource(),
                        messageKey = diagnosticId,
                        platform.pretty,
                    )
                }
            }
        }
    }
}

object AliasesAreNotIntersectingWithNaturalHierarchy : MergedTreeDiagnostic {

    override val diagnosticId: BuildProblemId = "validation.alias.intersects.with.natural.hierarchy"

    private val nhEntries = (Platform.naturalHierarchyExt - Platform.COMMON).entries

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        root.visitMapLikeProperties<Module>(Module::aliases) { _, aliasesRaw ->
            aliasesRaw.children.forEach { (aliasName, _, aliasValue) ->
                val aliasPlatforms = aliasValue.asList?.children
                    ?.mapNotNull { it.scalarValue<TraceableEnum<Platform>>() }?.leaves ?: return@forEach
                val similar = nhEntries.firstOrNull { aliasPlatforms == it.value }
                if (similar != null) {
                    problemReporter.reportBundleError(
                        source = aliasValue.trace.asBuildProblemSource(),
                        messageKey = diagnosticId,
                        aliasName,
                        similar.key.pretty,
                    )
                }
            }
        }
    }
}
