/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.unsafe
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.yaml.psi.YAMLPsiElement

object LibShouldHavePlatforms : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "product.type.does.not.have.default.platforms"

    context(ProblemReporterContext) override fun Module.analyze() {
        if (product::type.unsafe == ProductType.LIB && product::platforms.unsafe == null) {
            val isYaml = product::type.extractPsiElementOrNull()?.parent is YAMLPsiElement
            SchemaBundle.reportBundleError(
                property = product::type,
                messageKey = if (isYaml) diagnosticId else "product.type.does.not.have.default.platforms.amperlang",
                ProductType.LIB.schemaValue,
                level = Level.Fatal
            )
        }
    }
}