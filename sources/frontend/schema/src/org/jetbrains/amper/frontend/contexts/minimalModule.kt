/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.CollectingOnlyProblemReporterCtx
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.WholeFileBuildProblemSource
import org.jetbrains.amper.core.messages.replayProblemsTo
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.SchemaBundle
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
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.RefinedTree
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.get
import org.jetbrains.amper.frontend.tree.isEmptyOrNoValue
import org.jetbrains.amper.frontend.tree.onlyMapLike
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
    val reporterCtx = CollectingOnlyProblemReporterCtx()
    return with(copy(problemReporterCtx = reporterCtx)) {
        val rawModuleTree = readTree(
            moduleFilePath,
            type = types.getDeclaration<MinimalModule>(),
            reportUnknowns = false,
        )

        // We need to resolve defaults for the tree.
        val moduleTree = rawModuleTree
            ?.let { treeMerger.mergeTrees(it) }
            ?.appendDefaultValues()
            ?.resolveReferences() as? MapLikeValue<Merged>

        // Check if there is no "product" section.
        if (moduleTree == null || moduleTree["product"].isEmpty())
            problemReporter.reportMessage(
                BuildProblemImpl(
                    buildProblemId = "product.not.defined",
                    source = WholeFileBuildProblemSource(moduleFilePath.toNioPath()),
                    message = SchemaBundle.message("product.not.defined.empty"),
                    level = Level.Fatal,
                )
            )
        // Check if there is no "product.type" section (also, when type section has not value).
        else if (moduleTree["product"].values.onlyMapLike["type"].values.isEmptyOrNoValue()) {
            problemReporter.reportBundleError(
                source = moduleTree["product"].first().kTrace.asBuildProblemSource(),
                messageKey = "product.not.defined",
                level = Level.Fatal,
            )
        }

        if (reporterCtx.hasFatal) {
            // Rewind errors to the upper reporting context if something fatal had happened.
            // Otherwise, we will read the file again and report.
            reporterCtx.replayProblemsTo(this@tryReadMinimalModule.problemReporterCtx)
            return null
        }

        MinimalModuleHolder(
            moduleFilePath,
            this,
            // We can cast here because we know that minimal module
            // properties should be used outside any context.
            createSchemaNode<MinimalModule>(moduleTree as RefinedTree)
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