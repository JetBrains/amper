/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import com.sun.jna.Platform
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.ExtractOptions
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.util.OS
import java.nio.file.Path

class KotlinCompilerDownloader(
    val userCacheRoot: AmperUserCacheRoot,
) {
    companion object {
        const val AMPER_DEFAULT_KOTLIN_VERSION = "1.9.22"

        private const val MAVEN_CENTRAL_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"
        private const val KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
        private const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"
    }

    suspend fun downloadAndExtractKotlinCompiler(version: String): Path = downloadAndExtractFromMaven(
        mavenRepository = KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPOSITORY_URL,
        groupId = KOTLIN_GROUP_ID,
        artifactId = "kotlin-dist-for-ide",
        version = version,
        packaging = "jar",
    )

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
            OS.isMac || OS.isLinux -> "tar.gz"
            OS.isWindows -> "zip"
            else -> null
        } ?: return null

        val classifier: String = when (OS.type) {
            OS.Type.Mac -> when (Platform.ARCH) {
                "x86-64" -> "macos-x86_64"
                "aarch64" -> "macos-aarch64"
                else -> null
            }

            OS.Type.Windows -> when (Platform.ARCH) {
                "x86-64" -> "windows-x86_64"
                else -> null
            }

            OS.Type.Linux -> when (Platform.ARCH) {
                "x86-64" -> "linux-x86_64"
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
