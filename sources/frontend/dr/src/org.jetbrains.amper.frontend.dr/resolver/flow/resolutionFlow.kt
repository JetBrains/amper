/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.createOrReuseDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.MavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.logger
import org.jetbrains.amper.frontend.dr.resolver.parseCoordinates
import java.util.concurrent.ConcurrentHashMap

interface DependenciesFlow<T: DependenciesFlowType> {
    fun directDependenciesGraph(module: PotatoModule, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule

    fun directDependenciesGraph(modules: List<PotatoModule>, fileCacheBuilder: FileCacheBuilder.() -> Unit): DependencyNodeHolder {
        val node = DependencyNodeHolder(
            name = "root",
            children = modules.map { directDependenciesGraph(it, fileCacheBuilder) }
        )
        return node
    }
}

abstract class AbstractDependenciesFlow<T: DependenciesFlowType>(
    val flowType: T,
): DependenciesFlow<T> {

    protected fun MavenDependency.toFragmentDirectDependencyNode(fragment: Fragment, context: Context): DirectFragmentDependencyNodeHolder {
        val coordinates = parseCoordinates()
        val dependencyNode = context.toMavenDependencyNode(coordinates)

        val node = DirectFragmentDependencyNodeHolder(
            "dep:${fragment.module.userReadableName}:${fragment.name}:${dependencyNode}",
            dependencyNode,
            notation = this,
            fragment = fragment
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

    fun PotatoModule.getValidRepositories(): List<Repository> {
        val acceptedRepositories = mutableListOf<Repository>()
        for (repository in repositories()) {
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

    private fun PotatoModule.repositories(): List<Repository> =
        parts
            .filterIsInstance<RepositoriesModulePart>()
            .firstOrNull()
            ?.mavenRepositories
            ?.map { Repository(it.url, it.userName, it.password) }
            ?: defaultRepositories.map { Repository(it)}

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
