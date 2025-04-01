/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.KOTLIN_GROUP_ID
import org.jetbrains.amper.core.downloader.MAVEN_CENTRAL_REPOSITORY_URL
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.toRepositories
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.resolver.MavenResolver
import java.nio.file.Path
import kotlin.io.path.name

class KotlinArtifactsDownloader(
    val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) {

    private val mavenResolver = MavenResolver(userCacheRoot)

    /**
     * Downloads the implementation of the Kotlin Build Tools API (and its dependencies) in the given [version].
     *
     * The [version] should match the Kotlin version requested by the user, it is the version of the Kotlin compiler
     * that will be used behind the scenes.
     */
    suspend fun downloadKotlinBuildToolsImpl(version: String): Collection<Path> = downloadMavenArtifact(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-build-tools-impl",
        version = version,
    )

    /**
     * Downloads the implementation of the embeddable Kotlin compiler in the given [version].
     *
     * The [version] should match the Kotlin version requested by the user, it is the version of the Kotlin compiler
     * that will be used behind the scenes.
     */
    suspend fun downloadKotlinCompilerEmbeddable(version: String): List<Path> = downloadMavenArtifact(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-compiler-embeddable",
        version = version,
    )

    /**
     * Downloads the implementation of the embeddable Kotlin commonizer in the given [version].
     */
    suspend fun downloadKotlinCommonizerEmbeddable(version: String): List<Path> = downloadMavenArtifact(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-klib-commonizer-embeddable",
        version = version,
    )

    /**
     * Downloads the Kotlin Serialization compiler plugin for the given Kotlin [version].
     *
     * The [version] should match the Kotlin version requested by the user, the plugin will be added to
     * the Kotlin compiler command line of version [version].
     */
    suspend fun downloadKotlinSerializationPlugin(version: String): Path = downloadKotlinCompilerPlugin(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-serialization-compiler-plugin-embeddable",
        version = version,
    )

    /**
     * Downloads the Kotlin Parcelize compiler plugin for the given Kotlin [version].
     *
     * The [version] should match the Kotlin version requested by the user, the plugin will be added to
     * the Kotlin compiler command line of version [version].
     */
    suspend fun downloadKotlinParcelizePlugin(version: String): Path = downloadMavenArtifact(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-parcelize-compiler",
        version = version,
    ).single { it.name == "kotlin-parcelize-compiler-$version.jar" } // this one is not a far jar, but we still need one jar

    /**
     * Downloads the Kotlin No-arg compiler plugin for the given Kotlin [version].
     *
     * The [version] should match the Kotlin version requested by the user.
     */
    suspend fun downloadKotlinNoArgPlugin(version: String): Path = downloadKotlinCompilerPlugin(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-noarg-compiler-plugin-embeddable",
        version = version,
    )

    /**
     * Downloads the Kotlin All-open compiler plugin for the given Kotlin [version].
     *
     * The [version] should match the Kotlin version requested by the user.
     */
    suspend fun downloadKotlinAllOpenPlugin(version: String): Path = downloadKotlinCompilerPlugin(
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-allopen-compiler-plugin-embeddable",
        version = version,
    )

    /**
     * Downloads the Kotlin Compose compiler plugin for the given [kotlinVersion] (NOT the compose library version).
     *
     * The [kotlinVersion] should be the version of the Kotlin compiler itself (not Compose). For Kotlin versions
     * before 2.0.0-RC2, the legacy Compose compiler artifact will be downloaded in the correct version based on a
     * "well-known" mapping. For newer versions, the new Compose compiler artifact will be downloaded with the same
     * version as [kotlinVersion].
     */
    suspend fun downloadKotlinComposePlugin(kotlinVersion: String): Path {
        val legacyComposeVersion = legacyComposeCompilerVersionFor(kotlinVersion)
        return if (legacyComposeVersion != null) {
            downloadKotlinCompilerPlugin(
                groupId = "org.jetbrains.compose.compiler",
                artifactId = "compiler",
                version = legacyComposeVersion,
            )
        } else {
            downloadKotlinCompilerPlugin(
                groupId = KOTLIN_GROUP_ID,
                artifactId = "kotlin-compose-compiler-plugin-embeddable", // new artifact since 2.0.0-RC2
                version = kotlinVersion, // the new artifact uses a version matching the Kotlin version
            )
        }
    }

    private suspend fun downloadKotlinCompilerPlugin(groupId: String, artifactId: String, version: String): Path {
        val artifacts = downloadMavenArtifact(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
        )
        return artifacts.singleOrNull()
            ?: error("Only one file is expected to be resolved for a Kotlin compiler plugin, but got: "
                    + artifacts.joinToString(" "))
    }

    private suspend fun downloadMavenArtifact(groupId: String, artifactId: String, version: String): List<Path> =
        // using executeOnChangedInputs because currently DR takes ~3s even when the artifact is already cached
        executeOnChangedInputs.execute("resolve-$groupId-$artifactId-$version", emptyMap(), emptyList()) {
            val coordinates = "$groupId:$artifactId:$version"
            val resolved = mavenResolver.resolve(
                coordinates = listOf(coordinates),
                repositories = listOf(MAVEN_CENTRAL_REPOSITORY_URL).toRepositories(),
                scope = ResolutionScope.RUNTIME,
                platform = ResolutionPlatform.JVM,
                resolveSourceMoniker = coordinates,
            )
            return@execute ExecuteOnChangedInputs.ExecutionResult(resolved.toList())
        }.outputs
}

/**
 * Returns the legacy Compose (multiplatform) compiler plugin version matching the given [kotlinVersion], or null if
 * the new Compose compiler artifact with the same version as Kotlin should be used (i.e. Kotlin >= 2.0.0-RC2).
 *
 * **IMPORTANT:** The user-defined Compose version is only for runtime libraries and the Compose Gradle plugin.
 * The Compose compiler has a different versioning scheme, with a mapping to the Kotlin compiler versions.
 *
 * ### Implementation note
 *
 * This mapping should not have to be updated anymore, because the Compose compiler is now part of the Kotlin repository
 * and is released with the same version as Kotlin itself.
 *
 * The original mapping in this function came from the tables in
 * [the official documentation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html),
 * and the
 * [mapping in the Gradle plugin](https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposeCompilerCompatibility.kt).
 */
private fun legacyComposeCompilerVersionFor(kotlinVersion: String): String? = when (kotlinVersion) {
    "1.7.10" -> "1.3.0"
    "1.7.20" -> "1.3.2.2"
    "1.8.0" -> "1.4.0"
    "1.8.10" -> "1.4.2"
    "1.8.20" -> "1.4.5"
    "1.8.21" -> "1.4.7"
    "1.8.22" -> "1.4.8"
    "1.9.0-Beta" -> "1.4.7.1-beta"
    "1.9.0-RC" -> "1.4.8-beta"
    "1.9.0" -> "1.5.1"
    "1.9.10" -> "1.5.2"
    "1.9.20-Beta" -> "1.5.2.1-Beta2"
    "1.9.20-Beta2" -> "1.5.2.1-Beta3"
    "1.9.20-RC" -> "1.5.2.1-rc01"
    "1.9.20-RC2" -> "1.5.3-rc01"
    "1.9.20" -> "1.5.3"
    "1.9.21" -> "1.5.4"
    "1.9.22" -> "1.5.8.1"
    "1.9.23" -> "1.5.13.5"
    "1.9.24" -> "1.5.14"
    "2.0.0-Beta1" -> "1.5.4-dev1-kt2.0.0-Beta1"
    "2.0.0-Beta4" -> "1.5.9-kt-2.0.0-Beta4"
    "2.0.0-Beta5" -> "1.5.11-kt-2.0.0-Beta5"
    "2.0.0-RC1" -> "1.5.11-kt-2.0.0-RC1"
    else -> null // since 2.0.0-RC2, the Compose compiler version matches the Kotlin version (/!\ different artifact)
}
