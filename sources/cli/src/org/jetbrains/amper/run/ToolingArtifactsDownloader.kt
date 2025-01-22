/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.run

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.downloader.MAVEN_CENTRAL_REPOSITORY_URL
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.toRepositories
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.resolver.MavenResolver
import java.nio.file.Path

const val GOOGLE_REPOSITORY = "https://maven.google.com"

class ToolingArtifactsDownloader(
    userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) {

    private val mavenResolver = MavenResolver(userCacheRoot)

    suspend fun downloadHotReloadAgent(): List<Path> =
        downloadToolingArtifacts(
            listOf("org.jetbrains.compose:hot-reload-agent:${UsedVersions.hotReloadVersion}"),
            listOf(
                MAVEN_CENTRAL_REPOSITORY_URL,
                "https://packages.jetbrains.team/maven/p/firework/dev"
            ).toRepositories()
        )

    suspend fun downloadDevTools(): List<Path> = downloadToolingArtifacts(
        listOf(
            "org.jetbrains.compose:hot-reload-devtools:${UsedVersions.hotReloadVersion}",
            // TODO: use UsedVersions.composeVersion when AMPER-3764 will be resolved
            "org.jetbrains.compose.desktop:desktop-jvm-${DefaultSystemInfo.detect().familyArch}:${/*UsedVersions.composeVersion */ "1.7.1"}"
        ),
        listOf(
            MAVEN_CENTRAL_REPOSITORY_URL,
            GOOGLE_REPOSITORY,
            "https://packages.jetbrains.team/maven/p/firework/dev"
        ).toRepositories()
    )

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
