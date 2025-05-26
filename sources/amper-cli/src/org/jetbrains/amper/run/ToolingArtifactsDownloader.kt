/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.run

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.resolver.MavenResolver
import java.nio.file.Path

val GOOGLE_REPOSITORY = MavenRepository("https://maven.google.com")

class ToolingArtifactsDownloader(
    userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) {

    private val mavenResolver = MavenResolver(userCacheRoot)

    suspend fun downloadHotReloadAgent(): List<Path> =
        downloadToolingArtifacts(
            listOf("org.jetbrains.compose.hot-reload:hot-reload-agent:${UsedVersions.hotReloadVersion}"),
            listOf(
                MavenRepository("https://packages.jetbrains.team/maven/p/amper/compose-hot-reload"),
                MavenCentral,
            )
        )

    suspend fun downloadDevTools(): List<Path> = downloadToolingArtifacts(
        listOf(
            "org.jetbrains.compose.hot-reload:hot-reload-devtools:${UsedVersions.hotReloadVersion}",
            "org.jetbrains.compose.desktop:desktop-jvm-${DefaultSystemInfo.detect().familyArch}:${UsedVersions.composeVersion}",
        ),
        listOf(
            MavenRepository("https://packages.jetbrains.team/maven/p/amper/compose-hot-reload"),
            MavenCentral,
            GOOGLE_REPOSITORY,
        )
    )

    suspend fun downloadComposeDesktop(): List<Path> = downloadToolingArtifacts(
        listOf("org.jetbrains.compose.desktop:desktop-jvm-${DefaultSystemInfo.detect().familyArch}:${UsedVersions.composeVersion}"),
        listOf(
            MavenCentral,
            GOOGLE_REPOSITORY,
        )
    )

    suspend fun downloadSlf4jApi(): List<Path> = downloadToolingArtifacts(
        listOf("org.slf4j:slf4j-api:${UsedVersions.slf4jVersion}"),
        listOf(MavenCentral, MavenLocal)
    )

    suspend fun downloadSpringBootLoader(): Path = downloadToolingArtifacts(
        listOf("org.springframework.boot:spring-boot-loader:${UsedVersions.springBootVersion}"),
        listOf(MavenCentral, MavenLocal)
    ).single()

    private suspend fun downloadToolingArtifacts(coordinates: List<String>, repositories: List<Repository>): List<Path> =
        executeOnChangedInputs.execute("resolve-$coordinates", emptyMap(), emptyList()) {
            val resolved = mavenResolver.resolve(
                coordinates = coordinates,
                repositories = repositories,
                scope = ResolutionScope.RUNTIME,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = "Compose hot reload: $coordinates",
            )
            return@execute ExecuteOnChangedInputs.ExecutionResult(resolved.toList())
        }.outputs
}