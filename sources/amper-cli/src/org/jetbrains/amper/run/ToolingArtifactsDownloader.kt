/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.run

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.dr.resolver.CliReportingMavenResolver
import org.jetbrains.amper.frontend.dr.resolver.toIncrementalCacheResult
import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.frontend.schema.DiscouragedDirectDefaultVersionAccess
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.system.info.SystemInfo
import java.nio.file.Path

val GOOGLE_REPOSITORY = MavenRepository("https://maven.google.com")
val AMPER_DEV_REPOSITORY = MavenRepository("https://packages.jetbrains.team/maven/p/amper/amper")

class ToolingArtifactsDownloader(
    userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
) {

    private val mavenResolver = CliReportingMavenResolver(userCacheRoot, incrementalCache)

    suspend fun downloadHotReloadAgent(hotReloadVersion: String): List<Path> =
        downloadToolingArtifacts(
            listOf(
                MavenCoordinates("org.jetbrains.compose.hot-reload", "hot-reload-agent", hotReloadVersion),
                MavenCoordinates("org.jetbrains.compose.hot-reload", "hot-reload-runtime-jvm", hotReloadVersion),
            ),
        )

    suspend fun downloadDevTools(
        hotReloadVersion: String,
        composeVersion: String,
    ): List<Path> = downloadToolingArtifacts(
        listOf(
            MavenCoordinates("org.jetbrains.compose.hot-reload", "hot-reload-devtools", hotReloadVersion),
            MavenCoordinates("org.jetbrains.compose.desktop", "desktop-jvm-${SystemInfo.CurrentHost.familyArch}", composeVersion),
        ),
        buildList {
            addAll(listOf(MavenCentral, GOOGLE_REPOSITORY, AMPER_DEV_REPOSITORY))
        }
    )

    suspend fun downloadComposeDesktop(composeVersion: String): List<Path> = downloadToolingArtifacts(
        listOf(
            MavenCoordinates(
                "org.jetbrains.compose.desktop",
                "desktop-jvm-${SystemInfo.CurrentHost.familyArch}",
                composeVersion
            ),
        ),
        listOf(MavenCentral, GOOGLE_REPOSITORY)
    )

    @OptIn(DiscouragedDirectDefaultVersionAccess::class)
    suspend fun downloadSpringBootLoader(): Path = downloadToolingArtifacts(
        listOf(
            MavenCoordinates("org.springframework.boot", "spring-boot-loader", DefaultVersions.springBoot)
        )
    ).single()

    private suspend fun downloadToolingArtifacts(
        coordinates: List<MavenCoordinates>,
        repositories: List<Repository> = listOf(MavenCentral),
    ): List<Path> {
        val scope = ResolutionScope.RUNTIME
        val platform = ResolutionPlatform.JVM
        return incrementalCache.execute(
            key = "resolve-$coordinates ($scope, $platform)",
            inputValues = mapOf("repositories" to repositories.joinToString(",")),
            inputFiles = emptyList(),
        ) {
            val resolved = mavenResolver.resolve(
                coordinates = coordinates,
                repositories = repositories,
                scope = scope,
                platform = platform,
                resolveSourceMoniker = "Compose hot reload: $coordinates",
            )
            return@execute resolved.toIncrementalCacheResult()
        }.outputFiles
    }
}