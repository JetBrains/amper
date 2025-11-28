/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.MissingPropertiesHandler
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.isExplicitlySet
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.problems.reporting.replayProblemsTo
import org.jetbrains.yaml.psi.YAMLPsiElement

/**
 * Internal schema to read fields, which are crucial for contexts generation.
 * Must be fully compatible with [org.jetbrains.amper.frontend.schema.Module].
 */
class MinimalModule : SchemaNode() {
    var product by value<ModuleProduct>()

    var aliases by nullableValue<Map<String, List<TraceableEnum<Platform>>>>()

    @Misnomers("templates")
    var apply by nullableValue<List<TraceablePath>>()
}

val MinimalModule.unwrapAliases get() = aliases?.mapValues { it.value.leaves }.orEmpty()

internal val defaultContextsInheritance by lazy {
    PlatformsInheritance() + MainTestInheritance + DefaultInheritance
}

@OptIn(NonIdealDiagnostic::class)
internal fun BuildCtx.tryReadMinimalModule(moduleFilePath: VirtualFile): MinimalModuleHolder? {
    val collectingReporter = CollectingProblemReporter()
    val minimalModule = with(copy(problemReporter = collectingReporter)) {
        val rawModuleTree = readTree(
            moduleFilePath,
            declaration = types.getDeclaration<MinimalModule>(),
            reportUnknowns = false,
        )

        // We need to resolve defaults for the tree.
        val moduleTree = rawModuleTree
            .appendDefaultValues()

        val refined = TreeRefiner().refineTree(moduleTree, EmptyContexts)
            .resolveReferences()
        val delegate = object : MissingPropertiesHandler.Default(collectingReporter) {
            override fun onMissingRequiredPropertyValue(
                trace: Trace,
                valuePath: List<String>,
                relativeValuePath: List<String>,
            ) {
                when (valuePath) {
                    listOf("product") -> collectingReporter.reportBundleError(
                        source = trace.asBuildProblemSource(),
                        messageKey = "product.not.defined.empty",
                        buildProblemId = "product.not.defined",
                    )
                    listOf("product", "type") -> collectingReporter.reportBundleError(
                        source = trace.asBuildProblemSource(),
                        messageKey = "product.not.defined",
                    )
                    else -> super.onMissingRequiredPropertyValue(trace, valuePath, relativeValuePath)
                }
            }
        }
        createSchemaNode<MinimalModule>(refined, delegate)
    }
    if (minimalModule == null) {
        // Replay errors to the original reporter if we couldn't even create the minimal module.
        // Otherwise, messages will be reported when reading the full module, so we should swallow them here.
        collectingReporter.replayProblemsTo(problemReporter)
        return null
    }

    val specifiedUnsupportedPlatforms = minimalModule.product.specifiedUnsupportedPlatforms
    specifiedUnsupportedPlatforms.forEach { unsupportedPlatform ->
        problemReporter.reportUnsupportedPlatform(unsupportedPlatform, minimalModule.product.type)
    }
    if (specifiedUnsupportedPlatforms.isNotEmpty()) {
        return null
    }

    if (minimalModule.product.type == ProductType.LIB && !minimalModule.product::platforms.isExplicitlySet) {
        problemReporter.reportMissingExplicitPlatforms(minimalModule.product)
        return null
    }

    if (minimalModule.product.platforms.isEmpty()) {
        problemReporter.reportBundleError(
            source = minimalModule.product::platforms.asBuildProblemSource(),
            messageKey = "product.platforms.should.not.be.empty",
        )
        return null
    }

    return MinimalModuleHolder(
        moduleFilePath = moduleFilePath,
        buildCtx = this@tryReadMinimalModule,
        // We can cast here because we know that minimal module
        // properties should be used outside any context.
        module = minimalModule,
    )
}

private val ModuleProduct.specifiedUnsupportedPlatforms: List<TraceableEnum<Platform>>
    get() = platforms.filter { it.value !in type.supportedPlatforms }

private fun ProblemReporter.reportUnsupportedPlatform(
    unsupportedPlatform: TraceableEnum<Platform>,
    productType: ProductType,
) {
    reportBundleError(
        source = unsupportedPlatform.trace.asBuildProblemSource(),
        messageKey = "product.unsupported.platform",
        productType.schemaValue,
        unsupportedPlatform.value.pretty,
        productType.supportedPlatforms.joinToString { it.pretty },
        problemType = BuildProblemType.InconsistentConfiguration,
    )
}

private fun ProblemReporter.reportMissingExplicitPlatforms(product: ModuleProduct) {
    val isYaml = product::type.extractPsiElementOrNull()?.parent is YAMLPsiElement
    reportBundleError(
        source = product::type.asBuildProblemSource(),
        messageKey = if (isYaml) {
            "product.type.does.not.have.default.platforms"
        } else {
            "product.type.does.not.have.default.platforms.amperlang"
        },
        ProductType.LIB.schemaValue,
    )
}

internal class MinimalModuleHolder(
    val moduleFilePath: VirtualFile,
    val buildCtx: BuildCtx,
    val module: MinimalModule,
) {
    val appliedTemplates by lazy {
        module.apply?.map { it.value }.orEmpty()
    }

    val platformsInheritance by lazy {
        val aliases = module.aliases.orEmpty().mapValues { it.value.leaves }
        PlatformsInheritance(aliases)
    }

    val pathInheritance by lazy {
        // Order first by files and then by platforms.
        val appliedTemplates = module.apply?.map { it.value }.orEmpty()
        val filesOrder = appliedTemplates.mapNotNull { buildCtx.pathResolver.loadVirtualFileOrNull(it) } +
                listOf(moduleFilePath)
        PathInheritance(filesOrder)
    }

    val combinedInheritance by lazy {
        platformsInheritance + pathInheritance + MainTestInheritance + DefaultInheritance
    }
}