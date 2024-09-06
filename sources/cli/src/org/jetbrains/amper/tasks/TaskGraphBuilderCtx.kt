/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isParentOf
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms


data class ModuleSequenceCtx(
    val module: PotatoModule,
    val platform: Platform = Platform.COMMON,
    val isTest: Boolean = false,
    val buildType: BuildType = BuildType.Debug,
)

data class ModuleDependencySequenceCtx(
    val module: PotatoModule,
    val dependencyReason: ResolutionScope,
    val dependsOn: PotatoModule,
    // For decomposing declarations.
    val platform: Platform = Platform.COMMON,
    val isTest: Boolean = false,
    val buildType: BuildType = BuildType.Debug,
)

class TaskGraphBuilderCtx(
    val context: CliContext,
    val model: Model
) {
    val tasks = TaskGraphBuilder()
    val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

    fun build() = tasks.build()

    fun allFragments() = model.modules.asSequence().flatMap { it.fragments }

    fun allModules() = model.modules.asSequence().map { ModuleSequenceCtx(it) }

    fun Sequence<ModuleSequenceCtx>.alsoPlatforms(parent: Platform? = null) = flatMap { ctx ->
        ctx.module.targetLeafPlatforms.filter { parent?.isParentOf(it) == true }.map { ctx.copy(platform = it) }
    }

    fun Sequence<ModuleSequenceCtx>.alsoTests() = flatMap {
        listOf(it.copy(isTest = false), it.copy(isTest = true))
    }

    fun Sequence<ModuleSequenceCtx>.alsoBuildTypes(buildTypes: List<BuildType> = BuildType.entries) =
        flatMap { ctx -> buildTypes.map { ctx.copy(buildType = it) } }

    fun Sequence<ModuleSequenceCtx>.filterModuleType(type: (ProductType) -> Boolean) =
        filter { type(it.module.type) }

    inline fun <T> Sequence<T>.withEach(block: T.() -> Unit) = forEach(block)

    fun Sequence<ModuleSequenceCtx>.selectModuleDependencies(
        dependencyReason: ResolutionScope,
        block: ModuleDependencySequenceCtx.() -> Unit,
    ) = forEach { ctx ->
        ctx.module.forModuleDependency(
            ctx.isTest,
            ctx.platform,
            dependencyReason,
            context.userCacheRoot
        ) {
            block(
                ModuleDependencySequenceCtx(
                    module = ctx.module,
                    dependencyReason = dependencyReason,
                    dependsOn = it,
                    platform = ctx.platform,
                    isTest = ctx.isTest,
                    buildType = ctx.buildType,
                )
            )
        }
    }
}