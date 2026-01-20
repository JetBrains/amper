/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.aomBuilder.traceableString
import org.jetbrains.amper.frontend.dr.resolver.MavenResolver
import org.jetbrains.amper.frontend.dr.resolver.toDrMavenCoordinates
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.schema.toMavenCoordinates
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.download.downloadSingleArtifactJar
import org.jetbrains.amper.maven.parseMavenPluginXml
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Download specified maven plugins jars, extract their `plugin.xml` metadata and parse it.
 */
@UsedInIdePlugin
suspend fun prepareMavenPlugins(
    projectContext: AmperProjectContext,
    incrementalCache: IncrementalCache,
): List<MavenPluginXml> = coroutineScope prepare@{
    val userCacheRoot = AmperUserCacheRoot.fromCurrentUserResult() as? AmperUserCacheRoot ?: return@prepare emptyList()
    
    val mavenResolver = MavenResolver(userCacheRoot, incrementalCache)
    projectContext.externalMavenPluginDependencies.map { declaration ->
        async {
            val pluginJarFile = downloadPluginAndDirectDependencies(mavenResolver, declaration) ?: return@async null
            withJarEntry(pluginJarFile, "META-INF/maven/plugin.xml") {
                try {
                    parseMavenPluginXml(it)
                } catch (e: Exception) {
                    logger.warn("Failed to parse plugin.xml for ${declaration.coordinates}", e)
                    null
                }
            }
        }
    }.awaitAll().filterNotNull()
}

/**
 * Resolve and download the plugin and its direct dependencies.
 */
private suspend fun downloadPluginAndDirectDependencies(
    mavenResolver: MavenResolver,
    declaration: UnscopedExternalMavenDependency,
): Path? {
    val coordinates = declaration::coordinates.traceableString().toMavenCoordinates().toDrMavenCoordinates()
    return mavenResolver.downloadSingleArtifactJar(coordinates)
}

private val logger = LoggerFactory.getLogger("PrepareMavenPluginsKt")