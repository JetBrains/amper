/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.flow

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.dependency.resolution.CacheEntryKey
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenGroupAndArtifact
import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.asRootCacheEntryKey
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolderWithContext
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.spanBuilder
import org.jetbrains.amper.frontend.dr.resolver.toMavenCoordinates
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("resolutionFlow.kt")

interface DependenciesFlow<T: DependenciesFlowType> {
    fun directDependenciesGraph(
        module: AmperModule,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): ModuleDependencyNodeWithModuleAndContext

    fun directDependenciesGraph(
        modules: List<AmperModule>,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): RootDependencyNodeWithContext {
        return openTelemetry.spanBuilder("DR: Resolving direct graph").useWithoutCoroutines {
            val node = RootDependencyNodeWithContext(
                rootCacheEntryKey = resolutionCacheEntryKey(modules).asRootCacheEntryKey(),
                children = modules.map { directDependenciesGraph(it, fileCacheBuilder, openTelemetry, incrementalCache) },
                templateContext = emptyContext(fileCacheBuilder, openTelemetry, incrementalCache)
            )
            node
        }
    }

    fun resolutionCacheEntryKey(modules: List<AmperModule>): CacheEntryKey
}

abstract class AbstractDependenciesFlow<T: DependenciesFlowType>(
    val flowType: T,
): DependenciesFlow<T> {

    private val contextMap: ConcurrentHashMap<ContextKey, Context> = ConcurrentHashMap<ContextKey, Context>()

    internal fun MavenDependencyBase.toFragmentDirectDependencyNode(fragment: Fragment, context: Context): DirectFragmentDependencyNodeHolderWithContext {
        val dependencyNode = context.toMavenDependencyNode(toMavenCoordinates(), this is BomDependency)

        val node = DirectFragmentDependencyNodeHolderWithContext(
            dependencyNode,
            notation = this,
            fragment = fragment,
            templateContext = context,
            messages = emptyList(),
        )

        return node
    }

    fun AmperModule.getValidRepositories(): List<Repository> {
        val acceptedRepositories = mutableListOf<Repository>()
        for (repository in resolvableRepositories()) {
            if (repository is MavenRepository) {
                @Suppress("HttpUrlsUsage")
                if (repository.url.startsWith("http://")) {
                    // TODO: Special --insecure-http-repositories option or some flag in project.yaml
                    // to acknowledge http:// usage

                    // report only once per `url`
                    if (alreadyReportedHttpRepositories.put(repository.url, true) == null) {
                        logger.warn("http:// repositories are not secure and should not be used: ${repository.url}")
                    }

                    continue
                }

                if (!repository.url.startsWith("https://") && repository != MavenLocal) {

                    // report only once per `url`
                    if (alreadyReportedNonHttpsRepositories.put(repository.url, true) == null) {
                        logger.warn("Non-https repositories are not supported, skipping url: ${repository.url}")
                    }

                    continue
                }
            }

            acceptedRepositories.add(repository)
        }

        return acceptedRepositories
    }

    private fun AmperModule.resolvableRepositories(): List<Repository> =
        parts
            .filterIsInstance<RepositoriesModulePart>()
            .firstOrNull()
            ?.mavenRepositories
            ?.filter { it.resolve }
            ?.map { it.toRepository() }
            ?: defaultRepositories.map { it.toRepository() }

    protected fun AmperModule.resolveModuleContext(
        platforms: Set<ResolutionPlatform>,
        scope: ResolutionScope,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?,
    ): Context {
        val repositories = getValidRepositories()
        val context = contextMap.computeIfAbsent(
            ContextKey(
                scope,
                platforms,
                repositories.toSet()
            )
        ) { key ->
            Context {
                this.scope = key.scope
                this.platforms = key.platforms
                this.repositories = repositories
                this.cache = fileCacheBuilder
                this.openTelemetry = openTelemetry
                this.incrementalCache = incrementalCache
                fragments.firstOrNull()?.let { rootFragment ->
                    this.dependenciesBlocklist = rootFragment.settings.internal.excludeDependencies.mapNotNull {
                        val groupAndArtifact = it.split(":", limit = 2)
                        if (groupAndArtifact.size != 2) {
                            logger.error("Invalid `excludeDependencies` entry: $it"); null
                        } else {
                            MavenGroupAndArtifact(groupAndArtifact[0], groupAndArtifact[1])
                        }
                    }.toSet()
                }
            }
        }
        return context
    }

    companion object {
        private val alreadyReportedHttpRepositories = ConcurrentHashMap<String, Boolean>()
        private val alreadyReportedNonHttpsRepositories = ConcurrentHashMap<String, Boolean>()
    }
}

fun RepositoriesModulePart.Repository.toRepository() = when {
    this.url == SpecialMavenLocalUrl -> MavenLocal
    else -> MavenRepository(url, userName, password)
}

private fun String.toRepository() = RepositoriesModulePart.Repository(this, this).toRepository()

private val defaultRepositories = listOf(
    "https://repo1.maven.org/maven2",
    "https://maven.google.com/",
)

private data class ContextKey(
    val scope: ResolutionScope,
    val platforms: Set<ResolutionPlatform>,
    val repositories: Set<Repository>
)
