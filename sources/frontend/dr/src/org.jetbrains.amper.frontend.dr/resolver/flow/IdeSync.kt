/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.flow

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.dependency.resolution.CacheEntryKey
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.allFragmentDependencies
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolderWithContext
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNode
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.spanBuilder
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.slf4j.LoggerFactory
import kotlin.io.path.absolutePathString

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

private val logger = LoggerFactory.getLogger(IdeSync::class.java)

internal class IdeSync(
    dependenciesFlowType: DependenciesFlowType.IdeSyncType
): AbstractDependenciesFlow<DependenciesFlowType.IdeSyncType>(dependenciesFlowType) {

    override fun resolutionCacheEntryKey(modules: List<AmperModule>): CacheEntryKey {
        return CacheEntryKey.CompositeCacheEntryKey(
            listOf(
                "Project compile dependencies",
                flowType.aom.projectRoot.absolutePathString()
            )
        )
    }

    override fun directDependenciesGraph(
        module: AmperModule,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): ModuleDependencyNodeWithModuleAndContext =
        module.toGraph(fileCacheBuilder, openTelemetry, incrementalCache)

    private fun AmperModule.toGraph(
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): ModuleDependencyNodeWithModuleAndContext {
        val node = ModuleDependencyNodeWithModuleAndContext(
            graphEntryName = "module:${userReadableName}",
            children = fragments.flatMap { it.toGraph(fileCacheBuilder, openTelemetry, incrementalCache) },
            module = this,
            templateContext = emptyContext(fileCacheBuilder, openTelemetry, incrementalCache)
        )
        return node
    }

    /**
     * The method returns a list of direct fragment maven dependencies in the case of a single-platform module
     * (or more precisely if this fragment belongs to a single-platform module
     *  and depends on single-platform fragments of other modules only).
     *
     * In the case of a multiplatform project,
     * it resolves all 'COMPILE' maven dependencies declared for this fragment directly as well as those declared for fragments this
     * fragment depends on transitively (taking the flag 'exported' into account).
     * And return those as a plain list of the fragment direct dependencies.
     *
     * The reason for this is the following: maven dependencies taken from the fragments of other modules
     * should be resolved in the context of the target platforms of this fragment (the one being resolved).
     * Such dependencies cannot be reused from the result of the other fragment resolution targeting a broader set of platforms,
     * because only a subset of the KMP library's source sets would be added as actual dependency (in klib form) for the other fragment.
     * (those conforming to all platforms declared in the other multiplatform fragment).
     *
     * Being resolved in the context of the current fragment,
     * such dependencies provide the appropriate API and runtime for the being resolved fragment target platforms.
     *
     * Resolution of the complete COMPILE maven dependencies graph is performed by [Classpath] flow with COMPILE resolution scope.
     * (flag 'exported' takes effect in the case of native modules during graph resolution)
     */
    private fun Fragment.toGraph(
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): List<DirectFragmentDependencyNodeHolderWithContext> {
        return openTelemetry.spanBuilder("DR: Resolving direct graph").useWithoutCoroutines {
            val moduleDependencies = openTelemetry.spanBuilder("DR: directDependenciesGraph").useWithoutCoroutines {
                Classpath(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = platforms.mapNotNull { it.toResolutionPlatform() }.toSet(),
                        includeNonExportedNative = false,
                        isTest = isTest
                    )
                ).directDependenciesGraph(this, fileCacheBuilder, openTelemetry, incrementalCache)
            }

            if (hasSinglePlatformDependenciesOnly(moduleDependencies)) {
                val directDependencies = allFragmentDependencies(true)
                    .flatMap { externalDependencies }
                    .filterIsInstance<MavenDependencyBase>()
                    .distinct()
                    .mapNotNull { it.toGraph(this, fileCacheBuilder, openTelemetry, incrementalCache) }
                    .toList()
                // In a single-platform case we could rely on IDE dependencies resolution.
                // Exported dependencies of the fragment on other modules will be taken into account by IDE while preparing
                //  a fragment-related IDE module compilation classpath.
                return@useWithoutCoroutines directDependencies
            }

            val allMavenDeps = openTelemetry.spanBuilder("DR: allMavenDeps").useWithoutCoroutines {
                moduleDependencies
                    .distinctBfsSequence()
                    .filterIsInstance<DirectFragmentDependencyNodeHolderWithContext>()
                    .sortedByDescending { it.fragment == this }
//                    .sortedForIdeSync(this@toGraph)
                    .distinctBy { it.dependencyNode }
                    .mapNotNull {
                        val mavenDependencyNotation = it.notation
                        val context = mavenDependencyNotation.resolveFragmentContext(this, fileCacheBuilder, openTelemetry, incrementalCache)
                        context?.let { mavenDependencyNotation.toFragmentDirectDependencyNode(this, context) }
                    }.toList()
            }

            return@useWithoutCoroutines allMavenDeps
        }
    }

    /**
     * Returns all fragments in this module that target the given [platforms].
     */
    private fun Sequence<DirectFragmentDependencyNodeHolderWithContext>.sortedForIdeSync(fragment: Fragment): List<DirectFragmentDependencyNodeHolderWithContext> =
        this
            .groupBy { it.fragment }
            .ensureFirstFragmentDeps(fragment)
            .flatMap { it.value.sortedBy { it.graphEntryName } }

    private fun Map<Fragment, List<DirectFragmentDependencyNodeHolderWithContext>>.ensureFirstFragmentDeps(fragment: Fragment) =
        if (this.isEmpty() || this.entries.first() == fragment)
            this
        else {
            val loadedFragmentEntry = this.entries.firstOrNull { it.key == fragment }
            if (loadedFragmentEntry == null) {
                this
            } else
                buildMap {
                    put(loadedFragmentEntry.key, loadedFragmentEntry.value)
                    putAll(this@ensureFirstFragmentDeps - loadedFragmentEntry.key)
                }
        }

    private fun AmperModule.platforms(): Set<Platform> = fragments.flatMap { it.platforms }.toSet()

    private fun Fragment.hasSinglePlatformDependenciesOnly(moduleDependencies: ModuleDependencyNode): Boolean {
        if (platforms.size == 1 && module.fragments.all { it.platforms == platforms }) {
            val localModules = moduleDependencies
                .distinctBfsSequence()
                .filter { it is ModuleDependencyNode && it.notation is LocalModuleDependency }
                .map { ((it as ModuleDependencyNode).notation as LocalModuleDependency).module }
                .toSet()

            return localModules.all { it.platforms() == platforms }
        }
        return false
    }

    private fun MavenDependencyBase.toGraph(
        fragment: Fragment,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?,
    ): DirectFragmentDependencyNodeHolderWithContext? {
        val context = resolveFragmentContext(fragment, fileCacheBuilder, openTelemetry, incrementalCache)
            ?: return null
        return toFragmentDirectDependencyNode(fragment, context)
    }

    private fun MavenDependencyBase.resolveFragmentContext(
        fragment: Fragment,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?,
    ): Context? {
        val fragmentPlatforms = fragment.resolutionPlatforms ?: return null
        val scope = when (this) {
            is MavenDependency -> if (compile) ResolutionScope.COMPILE else ResolutionScope.RUNTIME
            is BomDependency -> ResolutionScope.COMPILE
        }
        return fragment.module.resolveModuleContext(fragmentPlatforms, scope, fileCacheBuilder, openTelemetry, incrementalCache)
    }
}

private val Fragment.resolutionPlatforms: Set<ResolutionPlatform>?
    get() =
        if (platforms.isEmpty()) {
            logger.warn("Fragment ${module.userReadableName}.$name has empty list of target platforms")
            null
        } else {
            platforms.mapNotNull {
                it.toResolutionPlatform() ?: run {
                    logger.error("Fragment ${module.userReadableName}.$name has target platform $it that is not supported for resolving external dependencies")
                    null
                }
            }.toSet().takeIf { it.isNotEmpty() }
        }
