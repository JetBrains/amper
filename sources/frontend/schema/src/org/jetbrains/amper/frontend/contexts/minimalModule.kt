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
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.replayProblemsTo

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
    PlatformsInheritance() + MainTestInheritance
}

@OptIn(NonIdealDiagnostic::class)
internal fun BuildCtx.tryReadMinimalModule(moduleFilePath: VirtualFile): MinimalModuleHolder? {
    val collectingReporter = CollectingProblemReporter()
    return with(copy(problemReporter = collectingReporter)) {
        val rawModuleTree = readTree(
            moduleFilePath,
            type = types.getDeclaration<MinimalModule>(),
            reportUnknowns = false,
        )

        // We need to resolve defaults for the tree.
        val moduleTree = treeMerger.mergeTrees(rawModuleTree)
            .appendDefaultValues()
            .resolveReferences()

        val refined = TreeRefiner().refineTree(moduleTree, EmptyContexts)
        val delegate = object : MissingPropertiesHandler.Default(collectingReporter) {
            override fun onMissingRequiredPropertyValue(
                trace: Trace,
                valuePath: List<String>,
                keyTrace: Trace?,
            ) {
                when (valuePath) {
                    listOf("product") if keyTrace != null -> collectingReporter.reportBundleError(
                        source = keyTrace.asBuildProblemSource(),
                        messageKey = "product.not.defined",
                        level = Level.Fatal,
                    )
                    listOf("product") -> collectingReporter.reportBundleError(
                        source = trace.asBuildProblemSource(),
                        messageKey = "product.not.defined.empty",
                        buildProblemId = "product.not.defined",
                        level = Level.Fatal,
                    )
                    listOf("product", "type") -> collectingReporter.reportBundleError(
                        source = trace.asBuildProblemSource(),
                        messageKey = "product.not.defined",
                        level = Level.Fatal,
                    )
                    else -> super.onMissingRequiredPropertyValue(trace, valuePath, keyTrace)
                }
            }
        }
        val instance = createSchemaNode<MinimalModule>(refined, delegate)

        if (collectingReporter.hasFatal) {
            // Replay errors to the original reporter if something fatal had happened.
            // Otherwise, we will read the file again and report.
            collectingReporter.replayProblemsTo(this@tryReadMinimalModule.problemReporter)
            return null
        }
        instance ?: return null

        MinimalModuleHolder(
            moduleFilePath = moduleFilePath,
            buildCtx = this@tryReadMinimalModule,
            // We can cast here because we know that minimal module
            // properties should be used outside any context.
            module = instance,
        )
    }
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