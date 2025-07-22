/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.WholeFileBuildProblemSource
import org.jetbrains.amper.core.messages.replayProblemsTo
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.Aliases
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.isEmptyOrNoValue
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.tree.values
import org.jetbrains.amper.frontend.types.getDeclaration

/**
 * Internal schema to read fields, which are crucial for contexts generation.
 * Must be fully compatible with [org.jetbrains.amper.frontend.schema.Module].
 */
class MinimalModule : SchemaNode() {
    var product by value<ModuleProduct>()

    var aliases by nullableValue<Map<String, Set<TraceableEnum<Platform>>>>()

    @Aliases("templates")
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

        val productProperty = moduleTree.children.firstOrNull { it.key == "product" }
        val possibleTypes = (productProperty?.value as? MapLikeValue)?.get("type")?.values

        // Check if there is "product" section.
        if (productProperty == null) {
            collectingReporter.reportBundleError(
                source = WholeFileBuildProblemSource(moduleFilePath.toNioPath()),
                messageKey = "product.not.defined.empty",
                buildProblemId = "product.not.defined",
                level = Level.Fatal,
            )
        }
        // Check if product types are present.
        else if (possibleTypes == null || possibleTypes.isEmptyOrNoValue()) {
            collectingReporter.reportBundleError(
                source = productProperty.kTrace.asBuildProblemSource(),
                messageKey = "product.not.defined",
                level = Level.Fatal,
            )
        }

        if (collectingReporter.hasFatal) {
            // Replay errors to the original reporter if something fatal had happened.
            // Otherwise, we will read the file again and report.
            collectingReporter.replayProblemsTo(this@tryReadMinimalModule.problemReporter)
            return null
        }

        val refined = TreeRefiner().refineTree(moduleTree, EmptyContexts)

        MinimalModuleHolder(
            moduleFilePath = moduleFilePath,
            buildCtx = this@tryReadMinimalModule,
            // We can cast here because we know that minimal module
            // properties should be used outside any context.
            module = createSchemaNode<MinimalModule>(refined)
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