/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeImpl
import org.jetbrains.amper.dependency.resolution.MavenGroupAndArtifact
import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.NoopSpanBuilder
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeInput
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.createOrReuseDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.ParsedCoordinates
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.parseCoordinates
import org.jetbrains.amper.frontend.dr.resolver.toMavenCoordinates
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("resolutionFlow.kt")

interface DependenciesFlow<T: DependenciesFlowType> {
    fun directDependenciesGraph(
        module: AmperModule,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource,
    ): ModuleDependencyNodeWithModule

    fun directDependenciesGraph(
        modules: List<AmperModule>,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource,
    ): RootDependencyNodeInput {
        return spanBuilder("DR: Resolving direct graph").useWithoutCoroutines {
            val node = RootDependencyNodeInput(
                resolutionId = resolutionId(modules),
                children = modules.map { directDependenciesGraph(it, fileCacheBuilder, spanBuilder) },
                templateContext = emptyContext(fileCacheBuilder, spanBuilder)
            )
            node
        }
    }

    fun resolutionId(modules: List<AmperModule>): String
}

abstract class AbstractDependenciesFlow<T: DependenciesFlowType>(
    val flowType: T,
): DependenciesFlow<T> {

    private val contextMap: ConcurrentHashMap<ContextKey, Context> = ConcurrentHashMap<ContextKey, Context>()

    internal fun MavenDependencyBase.toFragmentDirectDependencyNode(fragment: Fragment, context: Context): DirectFragmentDependencyNodeHolder {
        val dependencyNode = context.toMavenDependencyNode(toMavenCoordinates(), this is BomDependency)

        val node = DirectFragmentDependencyNodeHolder(
            dependencyNode,
            notation = this,
            fragment = fragment,
            templateContext = context,
            messages = emptyList(),
        )

        return node
    }

    /**
     * the caller should specify the parent node after this method is called
     */
    private fun Context.toMavenDependencyNode(coordinates: MavenCoordinates, isBom: Boolean): MavenDependencyNodeImpl {
        val mavenDependency = createOrReuseDependency(coordinates.groupId, coordinates.artifactId, coordinates.version, isBom = isBom)
        return getOrCreateNode(mavenDependency,null)
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
        spanBuilder: SpanBuilderSource? = null,
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
                this.spanBuilder = spanBuilder ?: { NoopSpanBuilder.create() }
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
    "https://maven.pkg.jetbrains.space/public/p/compose/dev"
)

private data class ContextKey(
    val scope: ResolutionScope,
    val platforms: Set<ResolutionPlatform>,
    val repositories: Set<Repository>
)
