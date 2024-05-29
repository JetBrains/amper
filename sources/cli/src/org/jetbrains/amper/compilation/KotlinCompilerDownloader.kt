/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path

class KotlinCompilerDownloader(
    val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
) {
    companion object {
        private const val MAVEN_CENTRAL_REPOSITORY_URL = "https://repo1.maven.org/maven2"
        private const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"
    }

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
                repositories = listOf(MAVEN_CENTRAL_REPOSITORY_URL),
                scope = ResolutionScope.RUNTIME,
                resolveSourceMoniker = coordinates,
            )
            return@execute ExecuteOnChangedInputs.ExecutionResult(resolved.toList())
        }.outputs

    /**
     * Downloads and extracts current system specific kotlin native.
     * Returns null if kotlin native is not supported on current system/arch.
     */
    suspend fun downloadAndExtractKotlinNative(version: String): Path? {
        // TODO should be forward-compatible in some way,
        //  i.e. support new os/arch combinations if future kotlin version support them
        //  probably the easiest way is to peek maven central page (could be cached!)
        //  https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-native/1.9.22/
        val packaging: String = when {
            OsFamily.current.isMac || OsFamily.current.isLinux -> "tar.gz"
            OsFamily.current.isWindows -> "zip"
            else -> null
        } ?: return null

        val arch = DefaultSystemInfo.detect().arch
        val classifier: String = when (OsFamily.current) {
            OsFamily.MacOs -> {
                when (arch) {
                    Arch.X64 -> "macos-x86_64"
                    Arch.Arm64 -> "macos-aarch64"
                    else -> null
                }
            }

            OsFamily.Windows -> when (arch) {
                Arch.X64 -> "windows-x86_64"
                else -> null
            }

            OsFamily.Linux,
            OsFamily.FreeBSD,
            OsFamily.Solaris -> when (arch) {
                Arch.X64 -> "linux-x86_64"
                else -> null
            }
        } ?: return null

        return downloadAndExtractFromMaven(
            mavenRepository = MAVEN_CENTRAL_REPOSITORY_URL,
            groupId = KOTLIN_GROUP_ID,
            artifactId = "kotlin-native-prebuilt",
            version = version,
            classifier = classifier,
            packaging = packaging,
            extractOptions = arrayOf(ExtractOptions.STRIP_ROOT),
        )
    }

    private suspend fun downloadAndExtractFromMaven(
        mavenRepository: String,
        groupId: String,
        artifactId: String,
        version: String,
        classifier: String? = null,
        packaging: String,
        vararg extractOptions: ExtractOptions,
    ): Path {
        val artifactUri = Downloader.getUriForMavenArtifact(
            mavenRepository = mavenRepository,
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
            packaging = packaging,
        )
        val downloadedArchive = Downloader.downloadFileToCacheLocation(artifactUri.toString(), userCacheRoot)
        return extractFileToCacheLocation(downloadedArchive, userCacheRoot, *extractOptions)
    }
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
    "1.9.23" -> "1.5.12.5"
    "1.9.24" -> "1.5.14"
    "2.0.0-Beta1" -> "1.5.4-dev1-kt2.0.0-Beta1"
    "2.0.0-Beta4" -> "1.5.9-kt-2.0.0-Beta4"
    "2.0.0-Beta5" -> "1.5.11-kt-2.0.0-Beta5"
    "2.0.0-RC1" -> "1.5.11-kt-2.0.0-RC1"
    else -> null // since 2.0.0-RC2, the Compose compiler version matches the Kotlin version (/!\ different artifact)
}
