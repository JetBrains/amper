/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitProduct
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.scalarValue
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLPsiElement

object LibShouldHavePlatforms : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "product.type.does.not.have.default.platforms"

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) =
        root.visitProduct { (typeValue, type, platformsValue, _, _) ->
            val isYaml = typeValue.trace.extractPsiElementOrNull()?.parent is YAMLPsiElement
            if (type == ProductType.LIB && platformsValue == null) {
                problemReporter.reportBundleError(
                    source = typeValue.trace.asBuildProblemSource(),
                    messageKey = if (isYaml) diagnosticId else "product.type.does.not.have.default.platforms.amperlang",
                    ProductType.LIB.schemaValue,
                    level = Level.Fatal,
                )
            }
        }
}

object ProductPlatformIsUnsupported : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "product.unsupported.platform"

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) =
        root.visitProduct { (_, type, _, platformsRaw, _) ->
            platformsRaw?.forEach {
                val platform = it.scalarValue<TraceableEnum<Platform>>()?.value ?: return@forEach
                if (platform !in type.supportedPlatforms) {
                    problemReporter.reportBundleError(
                        source = it.trace.asBuildProblemSource(),
                        messageKey = diagnosticId,
                        type.schemaValue,
                        platform.pretty,
                        type.supportedPlatforms.joinToString { it.pretty },
                        problemType = BuildProblemType.InconsistentConfiguration,
                    )
                }
            }
        }
}

object ProductPlatformsShouldNotBeEmpty : MergedTreeDiagnostic {
    override val diagnosticId: BuildProblemId = "product.platforms.should.not.be.empty"

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter) =
        root.visitProduct { (_, _, platformsValue, _, platforms) ->
            if (platformsValue != null && platforms.isNullOrEmpty()) {
                problemReporter.reportBundleError(
                    source = platformsValue.trace.asBuildProblemSource(),
                    messageKey = diagnosticId,
                    level = Level.Fatal,
                )
            }
        }
}
