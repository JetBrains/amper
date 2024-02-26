/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module

object UnknownQualifiers : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "product.unknown.qualifiers"

    private val knownPlatforms = Platform.values.map { it.schemaValue }

    context(ProblemReporterContext) override fun Module.analyze() {
        val knownAliases = aliases?.keys.orEmpty()
        val propertiesWithModifier = listOf(
            ::dependencies,
            ::settings,
            ::`test-dependencies`,
            ::`test-settings`,
        )

        propertiesWithModifier.forEach { property ->
            property.withoutDefault?.keys?.forEach { modifiers -> modifiers.validate(knownAliases) }
        }
    }

    context(ProblemReporterContext)
    private fun Modifiers.validate(knownAliases: Set<String>) {
        val unknownModifiers = this
            .filter { modifier -> modifier.value !in knownAliases }
            .filter { modifier -> modifier.value !in knownPlatforms }

        // In YAML `+` separated qualifiers are mapped to a single PsiElement (key of YAMLKeyValue), so we do a single report here.
        unknownModifiers.firstOrNull()?.let { unknownModifier ->
            SchemaBundle.reportBundleError(
                value = unknownModifier,
                messageKey = diagnosticId,
                unknownModifiers.joinToString(),
            )
        }
    }
}