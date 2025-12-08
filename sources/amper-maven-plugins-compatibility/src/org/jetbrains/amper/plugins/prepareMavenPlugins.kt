/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlBufferedReader
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.core.impl.multiplatform.InputStream
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.dr.resolver.MavenResolver
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.UnscopedExternalMavenDependency
import org.jetbrains.amper.frontend.types.maven.Configuration
import org.jetbrains.amper.frontend.types.maven.MavenPluginXml
import org.jetbrains.amper.frontend.types.maven.ParameterValue
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Download specified maven plugins jars, extract their `plugin.xml` metadata and parse it.
 */
@UsedInIdePlugin
suspend fun prepareMavenPlugins(
    projectContext: AmperProjectContext,
): List<MavenPluginXml> = coroutineScope prepare@{
    val userCacheRoot = AmperUserCacheRoot.fromCurrentUserResult() as? AmperUserCacheRoot ?: return@prepare emptyList()
    val incrementalCache = mavenPluginsIncrementalCache(projectContext)
    
    val mavenResolver = MavenResolver(userCacheRoot, incrementalCache)
    projectContext.externalMavenPluginDependencies.map { declaration ->
        async {
            val pluginJarFile = downloadPluginAndDirectDependencies(mavenResolver, declaration) ?: return@async null
            withJarEntry(pluginJarFile, "META-INF/maven/plugin.xml") {
                parseMavenPluginXml(it, declaration.coordinates)
            }
        }
    }.awaitAll().filterNotNull()
}

/**
 * Dedicated incremental cache for downloading maven plugins meta-information.
 */
private fun mavenPluginsIncrementalCache(projectContext: AmperProjectContext): IncrementalCache = IncrementalCache(
    stateRoot = projectContext.projectBuildDir / "maven.plugins.incremental.state",
    codeVersion = AmperBuild.mavenVersion,
    openTelemetry = GlobalOpenTelemetry.get(),
)

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
    ) { context ->
        val (group, module, version) = declaration.coordinates.split(":")
        val pluginNode = MavenDependencyNodeWithContext(context, group, module, version, false)
        RootDependencyNodeWithContext(
            templateContext = context,
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

@OptIn(ExperimentalXmlUtilApi::class)
private val xml: XML = XML {
    defaultPolicy {
        unknownChildHandler = UnknownChildHandler { reader, _, xmlDesc, _, _ ->
            // Do not recover anything, except `ParameterValue`.
            if (xmlDesc.serialDescriptor != Configuration.serializer().descriptor) return@UnknownChildHandler emptyList()
            // We need to use `peek()` function.
            if (reader !is XmlBufferedReader) return@UnknownChildHandler emptyList()

            @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
            val replacingReader = object : XmlReader by reader {
                override val localName: String get() = ParameterValue::class.simpleName!!
            }
            val result = buildList {
                while (replacingReader.eventType == EventType.START_ELEMENT) {
                    this += xml.decodeFromReader<ParameterValue>(replacingReader).copy(parameterName = reader.localName)
                    // We need to read next tag to read next potential element if there is the next element.
                    // 2 cases here - either `</configuration>` or the next parameter.
                    //
                    // Also, we can't just use `nextTag()`, because serializer expects the 
                    // closing </configuration> tag to be unread yet.
                    do {
                        if (reader.peek()?.eventType == EventType.END_ELEMENT) break
                        replacingReader.next()
                    } while(reader.eventType.isIgnorable)
                }
            }

            // Return data for the recovery.
            listOf(XML.ParsedData(0, result))
        }
    }
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