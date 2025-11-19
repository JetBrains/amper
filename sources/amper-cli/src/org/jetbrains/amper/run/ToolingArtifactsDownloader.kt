/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.run

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.resolver.toIncrementalCacheResult
import java.nio.file.Path

val GOOGLE_REPOSITORY = MavenRepository("https://maven.google.com")
val AMPER_DEV_REPOSITORY = MavenRepository("https://packages.jetbrains.team/maven/p/amper/amper")

class ToolingArtifactsDownloader(
    userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
) {

    private val mavenResolver = MavenResolver(userCacheRoot, incrementalCache)

    suspend fun downloadHotReloadAgent(hotReloadVersion: String): List<Path> =
        downloadToolingArtifacts(
            listOf(
                "org.jetbrains.compose.hot-reload:hot-reload-agent:$hotReloadVersion",
                "org.jetbrains.compose.hot-reload:hot-reload-runtime-jvm:$hotReloadVersion",
            ),
        )

    suspend fun downloadDevTools(
        hotReloadVersion: String,
        composeVersion: String,
    ): List<Path> = downloadToolingArtifacts(
        listOf(
            "org.jetbrains.compose.hot-reload:hot-reload-devtools:$hotReloadVersion",
            "org.jetbrains.compose.desktop:desktop-jvm-${DefaultSystemInfo.detect().familyArch}:$composeVersion",
        ),
        buildList {
            addAll(listOf(MavenCentral, GOOGLE_REPOSITORY, AMPER_DEV_REPOSITORY))
        }
    )

    suspend fun downloadComposeDesktop(composeVersion: String): List<Path> = downloadToolingArtifacts(
        listOf("org.jetbrains.compose.desktop:desktop-jvm-${DefaultSystemInfo.detect().familyArch}:$composeVersion"),
        listOf(MavenCentral, GOOGLE_REPOSITORY)
    )

    suspend fun downloadSpringBootLoader(): Path = downloadToolingArtifacts(
        listOf("org.springframework.boot:spring-boot-loader:${UsedVersions.springBootVersion}")
    ).single()

    private suspend fun downloadToolingArtifacts(
        coordinates: List<String>,
        repositories: List<Repository> = listOf(MavenCentral),
    ): List<Path> =
        incrementalCache.execute(
            key = "resolve-$coordinates",
            inputValues = emptyMap(),
            inputFiles = emptyList(),
        ) {
            val resolved = mavenResolver.resolve(
                coordinates = coordinates,
                repositories = repositories,
                scope = ResolutionScope.RUNTIME,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "Compose hot reload: $coordinates",
            )
            return@execute resolved.toIncrementalCacheResult()
        }.outputFiles
}