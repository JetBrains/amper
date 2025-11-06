/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.core.impl.multiplatform.InputStream
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.types.maven.MavenPluginXml
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.util.AmperCliIncrementalCache
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Download specified maven plugins jars, extract their `plugin.xml` metadata and parse it.
 */
internal suspend fun prepareMavenPlugins(
    context: CliContext,
    mavenResolver: MavenResolver = MavenResolver(
        userCacheRoot = context.userCacheRoot,
        incrementalCache = AmperCliIncrementalCache(context.buildOutputRoot),
    ),
): List<MavenPluginXml> = coroutineScope prepare@{
    context.projectContext.externalMavenPluginDependencies.map { declaration ->
        async {
            val pluginJarFile = downloadPluginAndDirectDependencies(mavenResolver, declaration) ?: return@async null
            withJarEntry(pluginJarFile, "META-INF/maven/plugin.xml") {
                parseMavenPluginXml(it, declaration.coordinates)
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
    // TODO Actually, here we need only the plugin jar. 
    //  Need to use [ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES] when it will work properly.
    val resolvedRoot = mavenResolver.resolveWithContext(
        repositories = listOf(MavenCentral),
        scope = ResolutionScope.RUNTIME,
        platform = ResolutionPlatform.JVM,
        resolveSourceMoniker = declaration.coordinates,
        resolutionDepth = ResolutionDepth.GRAPH_FULL,
    ) {
        val (group, module, version) = declaration.coordinates.split(":")
        val pluginNode = MavenDependencyNodeWithContext(this, group, module, version, false)
        RootDependencyNodeWithContext(
            templateContext = this,
            children = listOf(pluginNode)
        )
    }

    // We can safely assume that there is only one dependency here, because we created root holder that way.
    val pluginDep = resolvedRoot.root.children.first() as MavenDependencyNode
    return pluginDep.dependency.files()
        .filter { it.extension == "jar" }
        .mapNotNull { it.path }
        .singleOrNull()
}

private val xml = XML {
    defaultPolicy { ignoreUnknownChildren() }
}

/**
 * Parse maven plugin XML from a passed stream. Stream closing is on the caller side.
 */
@OptIn(ExperimentalXmlUtilApi::class)
private fun parseMavenPluginXml(inputStream: InputStream, moniker: String): MavenPluginXml? = try {
    val reader = KtXmlReader(inputStream.reader(Charsets.UTF_8))
    xml.decodeFromReader<MavenPluginXml>(reader)
} catch (e: Exception) {
    // TODO Need to pass a problem reporter here somehow to be able to report this on
    //  the plugin element that was provided in `project.yaml`.
    logger.error("Error while parsing plugin XML. Maven plugin \"$moniker\" will be ignored", e)
    null
}

private val logger = LoggerFactory.getLogger("prepareMavenPlugins")