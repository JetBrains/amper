/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module

private val Module.declaredLeaves get() = productIfDefined?.platforms?.leaves
private val nhEntries = (Platform.naturalHierarchyExt - Platform.COMMON).entries

object AliasesDontUseUndeclaredPlatform : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "validation.alias.use.undeclared.platform"

    context(ProblemReporterContext)
    override fun Module.analyze() = aliases?.forEach { (_, aliasPlatforms) ->
        aliasPlatforms
            .filter { it.value !in (declaredLeaves ?: return null) }
            .forEach { SchemaBundle.reportBundleError(it, diagnosticId, it.value.pretty) }
    }
}

object AliasesAreNotIntersectingWithNaturalHierarchy : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "validation.alias.intersects.with.natural.hierarchy"

    context(ProblemReporterContext)
    override fun Module.analyze() = aliases
        ?.mapNotNull { alias -> nhEntries.singleOrNull { alias.value.leaves == it.value }?.let { alias.key to it.key } }
        // TODO Report on a specific alias.
        ?.forEach { SchemaBundle.reportBundleError(::aliases.valueBase, diagnosticId, it.first, it.second) }
}

