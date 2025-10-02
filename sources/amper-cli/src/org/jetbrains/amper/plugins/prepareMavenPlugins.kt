/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.core.impl.multiplatform.InputStream
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.plugins.MavenPluginXml
import org.jetbrains.amper.frontend.project.mavenPluginXmlsDir
import org.jetbrains.amper.frontend.types.maven.DefaultMavenPluginXml
import org.jetbrains.amper.resolver.MavenResolver
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal suspend fun prepareMavenPlugins(
    context: CliContext,
    mavenResolver: MavenResolver = MavenResolver(context.userCacheRoot),
): List<MavenPluginXml> = coroutineScope prepare@{
    val externalPlugins = context.projectContext.externalMavenPluginDependencies
    val mavenPluginXmlsDir = context.projectContext.mavenPluginXmlsDir

    val pluginDeclarationsToCopyPaths = externalPlugins.map {
        val pluginXmlCopyName = it.coordinates.replace(":", "-")
        val pluginXmlCopyPath = (mavenPluginXmlsDir / "$pluginXmlCopyName.xml").apply {
            parent.createDirectories()
            if (!exists()) createFile()
        }
        it to pluginXmlCopyPath
    }

    // TODO Here later we should add download evading in case of directory contents are still valid.
    pluginDeclarationsToCopyPaths.mapNotNull { (declaration, copyPath) ->
        val resolvedRoot = mavenResolver.resolveWithContext(
            repositories = listOf(MavenCentral),
            scope = ResolutionScope.RUNTIME,
            platform = ResolutionPlatform.JVM,
            resolveSourceMoniker = declaration.coordinates,
        ) {
            val (group, module, version) = declaration.coordinates.split(":")
            RootDependencyNodeHolder(listOf(MavenDependencyNode(group, module, version, false)))
        }

        resolvedRoot.downloadDependencies()

        // Get plugin dependency. We can safely assume that there is only 
        // one dependency here, because we created root holder that way.
        val pluginDep = resolvedRoot.children.first() as MavenDependencyNode
        val pluginJarFile = pluginDep.dependency.files()
            .filter { it.extension == "jar" }
            .mapNotNull { it.getPath() }
            .singleOrNull()

        // Copy `plugin.xml`s from jars to a conventional location.
        pluginJarFile?.let { pluginJar ->
            async {
                withJarEntry(pluginJar, "META-INF/maven/plugin.xml") { input ->
                    @Suppress("UnstableApiUsage")
                    copyPath.outputStream().use { input.copyToAsync(it) }
                }
            }
        }
    }.awaitAll()

    pluginDeclarationsToCopyPaths.mapNotNull { (declaration, copyPath) ->
        copyPath.inputStream().use { parseMavenPluginXml(it, declaration.coordinates) }
    }
}

internal val xml = XML {
    defaultPolicy { ignoreUnknownChildren() }
}

/**
 * Parse maven plugin xml from a passed stream. Stream closing is on the caller side.
 */
@OptIn(ExperimentalXmlUtilApi::class)
private fun parseMavenPluginXml(inputStream: InputStream, moniker: String): MavenPluginXml? = try {
    val reader = KtXmlReader(inputStream.reader(Charsets.UTF_8))
    xml.decodeFromReader<DefaultMavenPluginXml>(reader)
} catch (e: SerializationException) {
    logger.error("Error while parsing plugin XML, plugin \"$moniker\" will be ignored", e)
    null
}

private val logger = LoggerFactory.getLogger("prepareMavenPlugins")