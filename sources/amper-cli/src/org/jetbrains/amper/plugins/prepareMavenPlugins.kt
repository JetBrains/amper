/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.withJarEntry
import org.jetbrains.amper.frontend.project.mavenPluginXmlsDir
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

suspend fun prepareMavenPlugins(
    context: CliContext,
    mavenResolver: MavenResolver = MavenResolver(context.userCacheRoot),
) = coroutineScope prepare@{
    spanBuilder("Prepare Maven plugins").use {
        val externalPlugins = context.projectContext.externalPluginDependencies ?: return@use

        externalPlugins.mapNotNull { extDep ->
            val pluginXmlCopyName = extDep.coordinates.replace(":", "-")
            val pluginXmlCopyPath = (context.projectContext.mavenPluginXmlsDir / "$pluginXmlCopyName.xml").apply {
                parent.createDirectories()
                if (!exists()) createFile()
            }

            val resolvedRoot = mavenResolver.resolveWithContext(
                repositories = listOf(MavenCentral),
                scope = ResolutionScope.RUNTIME,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = extDep.coordinates,
            ) {
                val (group, module, version) = extDep.coordinates.split(":")
                RootDependencyNodeHolder(listOf(MavenDependencyNode(group, module, version, false)))
            }

            resolvedRoot.downloadDependencies()

            // Get plugin dependency. We can safely assume that there is only 
            // one dependency here, because we created root holder that way.
            val pluginDep = resolvedRoot.children.first() as MavenDependencyNode
            val pluginJarFile = pluginDep.dependency.files()
                .filter { it.extension == "jar" }
                .mapNotNull { it.getPath() }
                // We want to get only relevant artifacts.
                .singleOrNull { it.pathString.contains(pluginDep.module) }

            // Copy `plugin.xml`s from jars to a conventional location.
            pluginJarFile?.let { pluginJar ->
                async {
                    withJarEntry(pluginJar, "META-INF/maven/plugin.xml") { input ->
                        @Suppress("UnstableApiUsage")
                        pluginXmlCopyPath.outputStream().use { input.copyToAsync(it) }
                    }
                }
            }
        }.awaitAll()
    }
}