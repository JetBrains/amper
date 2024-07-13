/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Performs the resolution of direct module dependencies.
 *
 * The resulting graph contains a node for every module dependency
 * as well as direct maven dependencies of that module.
 *
 * Maven dependencies from the resulted graph are intended to be converted to Ide module dependencies (1:1)
 *
 * It doesn't download anything.
 *
 * Graph:
 * ```
 * ┌────┐
 * │root├─────────────────────────────┐
 * └──┬─┘                             │
 *    │                               │
 *    │                               │
 * ┌──▼───────────┐                 ┌─▼────────────┐
 * │potato-module1│...              │potato-moduleN│
 * └──┬───────────┘                 └─┬────────────┘
 *    │                               │
 *    ├───────────────┐               ├──────────────┐
 *    │               │               │              │
 * ┌──▼────────┐   ┌──▼────────┐   ┌──▼────────┐   ┌─▼─────────┐
 * │fragment1.1│...│fragment1.N│   │fragmentN.1│...│fragmentN.N│
 * └──┬────────┘   └───────────┘   └───────────┘   └───────────┘
 *    │
 *    ├──────────┐
 *    │          │
 * ┌──▼───┐   ┌──▼───┐
 * │maven1│...│mavenN│
 * └──────┘   └──────┘
 * ```
 */
internal class IdeSync(
    dependenciesFlowType: DependenciesFlowType.IdeSyncType,
): AbstractDependenciesFlow<DependenciesFlowType.IdeSyncType>(dependenciesFlowType) {

    private val contextMap: ConcurrentHashMap<ContextKey, Context> = ConcurrentHashMap<ContextKey, Context>()

    override fun directDependenciesGraph(module: PotatoModule, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule =
        module.toGraph(fileCacheBuilder)

    private fun PotatoModule.toGraph(fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule {
        val repositories = getValidRepositories()

        val node = ModuleDependencyNodeWithModule(
            name = "module:${userReadableName}",
            children = fragments.flatMap { it.toGraph(repositories, fileCacheBuilder) },
            module = this
        )
        return node
    }

    private fun Fragment.toGraph(repositories: List<Repository>, fileCacheBuilder: FileCacheBuilder.() -> Unit): List<DirectFragmentDependencyNodeHolder> {
        val dependencies = externalDependencies.filterIsInstance<MavenDependency>()

        val sharedModuleDependencies = if (platforms.size != 1)
            emptyList()
        else {
            // Find all multiplatform fragments this fragment F(P1) depends on.
            // F(P1) -> F(P1,P2,P3)
            // F(P1) -> F(P1,P6,P7,P8)
            // and add external dependencies of those fragments to this one directly.
            // Such dependencies can't be taken from multiplatform fragment dependency
            // because such dependencies would be resolved to kmp-libraries in their own multiplatform context
            // so that only sub-set of the library's source sets would be added as actual dependency (in klib form).
            // (those conforming to all platforms declared in a multiplatform fragment).
            // This way such dependencies should be resolved in context of the current single-platform fragment F(P1) as well
            // in order to provide appropriate API and runtime for single-platform compilation and execution.
            // Todo (AB) : Take flag 'exported' into account and go down to fragment dependencies tree transitively in that case picking up external dependencies there as well)
            val allFragmentDeps = fragmentDependencies
                .groupBy(keySelector =  { it.target.module.userReadableName }, valueTransform = { it.target.name })

            flowType.aom.modules.mapNotNull { module ->
                // resolve external dependencies of ALL multiplatform fragments among current fragment dependencies
                allFragmentDeps[module.userReadableName]?.let { deps ->
                    module.fragments.filter { it.name in deps && it.platforms.size > 1 }.map { it.externalDependencies }.flatten()
                }
            }.flatten().filterIsInstance<MavenDependency>()
        }

        val allMavenDeps = (dependencies + sharedModuleDependencies)
            .map { it.toGraph(this, repositories, fileCacheBuilder) }

        return allMavenDeps
    }

    private fun MavenDependency.toGraph(fragment: Fragment, repositories: List<Repository>, fileCacheBuilder: FileCacheBuilder.() -> Unit): DirectFragmentDependencyNodeHolder {
        val context = contextMap.computeIfAbsent(
            ContextKey(
                // Todo (AB) : Prefer COMPILE here (and don't use Ide module dependencies as a runtime classpath)
                if (runtime) ResolutionScope.RUNTIME else ResolutionScope.COMPILE,
                fragment.resolutionPlatforms,
            )
        ) { key ->
            Context {
                this.scope = key.scope
                this.platforms = key.platforms
                this.cache = fileCacheBuilder
            }
        }.let {
            if (repositories.toSet() != it.settings.repositories.toSet()) {
                it.copyWithNewNodeCache(emptyList(), repositories)
            } else it
        }

        val node = toFragmentDirectDependencyNode(fragment, context)
        return node
    }
}

private data class ContextKey(
    val scope: ResolutionScope,
    val platforms: Set<ResolutionPlatform>
)

private val Fragment.resolutionPlatforms: Set<ResolutionPlatform>
    get() = (platforms.mapNotNull {
        it.toResolutionPlatform() ?: run {
            logger.error("${name}: Platform $it is not supported for resolving external dependencies")
            null
        }
    }.takeIf { it.isNotEmpty() }?.toSet()
        ?: throw IllegalStateException("Can't start resolution for the fragment with platforms ${platforms.toSet()}"))
