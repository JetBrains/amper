/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
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
 * ┌──▼──────────┐                  ┌─▼───────────┐
 * │amper-module1│...               │amper-moduleN│
 * └──┬──────────┘                  └─┬───────────┘
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

    override fun directDependenciesGraph(module: AmperModule, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule =
        module.toGraph(fileCacheBuilder)

    private fun AmperModule.toGraph(fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule {
        val repositories = getValidRepositories()

        val node = ModuleDependencyNodeWithModule(
            name = "module:${userReadableName}",
            children = fragments.flatMap { it.toGraph(repositories, fileCacheBuilder) },
            module = this,
            templateContext = emptyContext(fileCacheBuilder)
        )
        return node
    }

   /**
    * The method returns list of direct fragment maven dependencies in the case of single-platform module
    * (or more precisely if this fragment belongs to a single-platform module,
    *  and depends on single-platform fragments of other modules only).
    *
    * In the case of a multiplatform project,
    * it resolves all 'COMPILE' maven dependencies declared for this fragment directly as well as those declared for fragments this
    * fragment depends on transitively (taking the flag 'exported' into account).
    * And return those as a plain list of this fragment direct dependencies.
    *
    * The reason for this is the following: maven dependencies taken from the fragments of other modules
    * should be resolved in the context of the target platforms of this fragment (the one being resolved).
    * Such dependencies cannot be reused from the result of the other fragment resolution targeting a broader set of platforms,
    * because only a sub-set of the KMP library's source sets would be added as actual dependency (in klib form) for the other fragment.
    * (those conforming to all platforms declared in the other multiplatform fragment).
    *
    * Being resolved in the context of the current fragment,
    * such dependencies provide the appropriate API and runtime for the being resolved fragment target platforms.
    *
    * Resolution of the complete COMPILE maven dependencies graph is performed by [Classpath] flow with COMPILE resolution scope.
    * (flag 'exported' takes effect in the case of native modules during graph resolution)
    */
    private fun Fragment.toGraph(repositories: List<Repository>, fileCacheBuilder: FileCacheBuilder.() -> Unit): List<DirectFragmentDependencyNodeHolder> {
        val moduleDependencies = Classpath(DependenciesFlowType.ClassPathType(
            scope = ResolutionScope.COMPILE,
            platforms = platforms.mapNotNull { it.toResolutionPlatform() }.toSet(),
            includeNonExportedNative = false,
            isTest = isTest)
        ).directDependenciesGraph(module, fileCacheBuilder)

        val directDependencies = externalDependencies
            .filterIsInstance<MavenDependency>()
            .map { it.toGraph(this, repositories, fileCacheBuilder) }

        if (hasSinglePlatformDependenciesOnly(moduleDependencies)) {
            return directDependencies
        }

        val allMavenDeps = moduleDependencies
            .distinctBfsSequence()
            .filterIsInstance<DirectFragmentDependencyNodeHolder>()
            .filter { it.notation is MavenDependency }
            .sortedByDescending { it.fragment == this }
            .distinctBy { it.dependencyNode }
            .map {
                val mavenDependencyNotation = it.notation as MavenDependency
                val context = mavenDependencyNotation.resolveContext(this, fileCacheBuilder, repositories)
                mavenDependencyNotation.toFragmentDirectDependencyNode(this, context)
            }.toList()

        return allMavenDeps
    }

    private fun AmperModule.platforms(): Set<Platform> = fragments.flatMap { it.platforms }.toSet()

    private fun Fragment.hasSinglePlatformDependenciesOnly(moduleDependencies: ModuleDependencyNodeWithModule): Boolean {
        if (platforms.size == 1 && module.fragments.all { it.platforms == platforms }) {
            val localModules = moduleDependencies
                .distinctBfsSequence()
                .filter { it is ModuleDependencyNodeWithModule && it.notation is LocalModuleDependency }
                .map { ((it as ModuleDependencyNodeWithModule).notation as LocalModuleDependency).module }
                .toSet()

            return localModules.all { it.platforms() == platforms }
        }
        return false
    }

    private fun MavenDependency.toGraph(fragment: Fragment, repositories: List<Repository>, fileCacheBuilder: FileCacheBuilder.() -> Unit): DirectFragmentDependencyNodeHolder {
        val context = resolveContext(fragment, fileCacheBuilder, repositories)
        val node = toFragmentDirectDependencyNode(fragment, context)
        return node
    }

    private fun MavenDependency.resolveContext(
        fragment: Fragment,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        repositories: List<Repository>
    ): Context {
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
        return context
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
        ?: error("Can't start resolution for the fragment with platforms ${platforms.toSet()}"))
