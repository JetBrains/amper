/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.UnresolvedMavenDependencyNode
import org.jetbrains.amper.dependency.resolution.createOrReuseDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.MavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.getDefaultAmperFileCacheBuilder
import org.jetbrains.amper.frontend.dr.resolver.parseCoordinates
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("resolutionFlow.kt")

interface DependenciesFlow<T: DependenciesFlowType> {
    fun directDependenciesGraph(
        module: AmperModule,
        fileCacheBuilder: FileCacheBuilder.() -> Unit = getDefaultAmperFileCacheBuilder()
    ): ModuleDependencyNodeWithModule

    fun directDependenciesGraph(
        modules: List<AmperModule>,
        fileCacheBuilder: FileCacheBuilder.() -> Unit = getDefaultAmperFileCacheBuilder()
    ): DependencyNodeHolder {
        val node = DependencyNodeHolder(
            name = "root",
            children = modules.map { directDependenciesGraph(it, fileCacheBuilder) },
            emptyContext(fileCacheBuilder)
        )
        return node
    }
}

abstract class AbstractDependenciesFlow<T: DependenciesFlowType>(
    val flowType: T,
): DependenciesFlow<T> {

    protected fun MavenDependency.toFragmentDirectDependencyNode(fragment: Fragment, context: Context): DirectFragmentDependencyNodeHolder {
        val coordinates = parseCoordinates()
        val dependencyNode = coordinates
            ?.let { context.toMavenDependencyNode(coordinates) }
            ?: UnresolvedMavenDependencyNode(this.coordinates.value, context)

        val node = DirectFragmentDependencyNodeHolder(
            dependencyNode,
            notation = this,
            fragment = fragment,
            templateContext = context
        )

        return node
    }

    /**
     * the caller should specify the parent node after this method is called
     */
    private fun Context.toMavenDependencyNode(coordinates: MavenCoordinates): MavenDependencyNode {
        val mavenDependency = createOrReuseDependency(coordinates.groupId, coordinates.artifactId, coordinates.version)
        return getOrCreateNode(mavenDependency,null)
    }

    fun AmperModule.getValidRepositories(): List<Repository> {
        val acceptedRepositories = mutableListOf<Repository>()
        for (repository in resolvableRepositories()) {
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

            if (!repository.url.startsWith("https://")) {

                // report only once per `url`
                if (alreadyReportedNonHttpsRepositories.put(repository.url, true) == null) {
                    logger.warn("Non-https repositories are not supported, skipping url: ${repository.url}")
                }

                continue
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
            ?.map { Repository(it.url, it.userName, it.password) }
            ?: defaultRepositories.map { Repository(it)}

    /**
     * Returns all fragments in this module that target the given [platform].
     * If [includeTestFragments] is false, only production fragments are returned.
     */
    protected fun AmperModule.fragmentsTargeting(platforms: Set<Platform>, includeTestFragments: Boolean): List<Fragment> =
        fragments
            .filter { (includeTestFragments || !it.isTest) && it.platforms.containsAll(platforms) }
            .sortedBy { it.name }
            .ensureFirstFragment(platforms)

    private fun List<Fragment>.ensureFirstFragment(platforms: Set<Platform>) =
        if (this.isEmpty() || this[0].platforms == platforms)
            this
        else {
            val fragmentWithPlatform = this.firstOrNull { it.platforms == platforms }
            if (fragmentWithPlatform == null) {
                this
            } else
                buildList {
                    add(fragmentWithPlatform)
                    addAll(this@ensureFirstFragment - fragmentWithPlatform)
                }
        }

    companion object {
        private val alreadyReportedHttpRepositories = ConcurrentHashMap<String, Boolean>()
        private val alreadyReportedNonHttpsRepositories = ConcurrentHashMap<String, Boolean>()
    }
}

private val defaultRepositories = listOf(
    "https://repo1.maven.org/maven2",
    "https://maven.google.com/",
    "https://maven.pkg.jetbrains.space/public/p/compose/dev"
)
