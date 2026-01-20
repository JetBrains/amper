/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.dr.resolver.MavenResolver
import org.jetbrains.amper.maven.download.downloadSingleArtifactJar
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.maven.contributor.filterJarProjects
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.div

private val logger = LoggerFactory.getLogger("PluginXmlObtainer")

private val pluginsToIgnore = setOf(
    // covered by built-in functionality in Amper
    PluginId("org.apache.maven.plugins", "maven-compiler-plugin"),
    PluginId("org.apache.maven.plugins", "maven-jar-plugin"),
    PluginId("org.apache.maven.plugins", "maven-resources-plugin"),
    PluginId("org.apache.maven.plugins", "maven-clean-plugin"),
    PluginId("org.apache.maven.plugins", "maven-install-plugin"),
    PluginId("org.apache.maven.plugins", "maven-deploy-plugin"),
    PluginId("org.apache.maven.plugins", "maven-site-plugin"),
    PluginId("org.apache.maven.plugins", "maven-source-plugin"),
    PluginId("org.apache.maven.plugins", "maven-javadoc-plugin"),

    // plugins we process
    PluginId("org.jetbrains.kotlin", "kotlin-maven-plugin"),
    PluginId("org.springframework.boot", "spring-boot-maven-plugin"),
)

private data class PluginId(val groupId: String, val artifactId: String)

internal suspend fun Set<MavenProject>.extractUnknownPluginXmls(
    userCacheRoot: AmperUserCacheRoot,
    codeVersion: String = "1.0-SNAPSHOT",
): List<MavenPluginXml> =
    coroutineScope {
        val unknownPlugins = filterJarProjects().flatMap { project ->
            project.model.build?.plugins?.filter { plugin ->
                !pluginsToIgnore.contains(PluginId(plugin.groupId, plugin.artifactId))
            }?.map { plugin -> project to plugin } ?: emptyList()
        }

        if (unknownPlugins.isEmpty()) return@coroutineScope emptyList()

        val incrementalCache = IncrementalCache(
            stateRoot = userCacheRoot.path / "maven-converter" / "plugins.cache",
            codeVersion = codeVersion,
            openTelemetry = GlobalOpenTelemetry.get(),
        )

        val mavenResolver = MavenResolver(userCacheRoot, incrementalCache)

        unknownPlugins.map { (_, plugin) ->
            async {
                logger.info("Downloading plugin ${plugin.groupId}:${plugin.artifactId}:${plugin.version}")
                downloadAndParsePluginXml(mavenResolver, plugin)
            }
        }
            .awaitAll()
            .filterNotNull()
    }

private suspend fun downloadAndParsePluginXml(
    mavenResolver: MavenResolver,
    plugin: Plugin,
): MavenPluginXml? {
    val coordinates = MavenCoordinates(
        groupId = plugin.groupId,
        artifactId = plugin.artifactId,
        version = plugin.version,
        packagingType = "maven-plugin",
    )

    val pluginJarFile = downloadPlugin(mavenResolver, coordinates) ?: return null

    return withJarEntry(pluginJarFile, "META-INF/maven/plugin.xml") {
        try {
            parseMavenPluginXml(it)
        } catch (e: Exception) {
            logger.warn("Failed to parse plugin.xml for $coordinates", e)
            null
        }

    }
}

private suspend fun downloadPlugin(
    mavenResolver: MavenResolver,
    coordinates: MavenCoordinates,
): Path? {
    return try {
        mavenResolver.downloadSingleArtifactJar(coordinates)
    } catch (e: Exception) {
        logger.warn("Failed to download plugin $coordinates", e)
        null
    }
}
