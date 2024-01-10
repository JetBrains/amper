/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.util.OS
import java.nio.file.Path
import com.sun.jna.Platform
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.downloader.ExtractOptions
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object KotlinCompilerUtil {
    const val AMPER_DEFAULT_KOTLIN_VERSION = "1.9.22"

    suspend fun downloadAndExtractKotlinCompiler(version: String, userCacheRoot: AmperUserCacheRoot): Path {
        val kotlinDistUrl = Downloader.getUriForMavenArtifact(
            mavenRepository = KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPOSITORY_URL,
            groupId = ARTIFACT_GROUP_ID,
            artifactId = "kotlin-dist-for-ide",
            version = version,
            packaging = "jar",
        ).toString()
        val kotlinDistJar = Downloader.downloadFileToCacheLocation(kotlinDistUrl, userCacheRoot)
        return extractFileToCacheLocation(kotlinDistJar, userCacheRoot)
    }

    /**
     * Downloads and extracts current system specific kotlin native.
     * Returns null if kotlin native is not supported on current system/arch.
     */
    suspend fun downloadAndExtractKotlinNative(version: String, userCacheRoot: AmperUserCacheRoot): Path? {
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

        val kotlinNativeUrl = Downloader.getUriForMavenArtifact(
            mavenRepository = MAVEN_CENTRAL_REPOSITORY_URL,
            groupId = ARTIFACT_GROUP_ID,
            artifactId = "kotlin-native-prebuilt",
            version = version,
            classifier = classifier,
            packaging = packaging,
        ).toString()
        val kotlinNativeArchive = Downloader.downloadFileToCacheLocation(kotlinNativeUrl, userCacheRoot)
        return extractFileToCacheLocation(kotlinNativeArchive, userCacheRoot, ExtractOptions.STRIP_ROOT)
    }

    inline fun <R> withKotlinCompilerArgFile(args: List<String>, tempRoot: AmperProjectTempRoot, block: (Path) -> R): R {
        // escaping rules from https://github.com/JetBrains/kotlin/blob/6161f44d91e235750077e1aaa5faff7047316190/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/preprocessCommandLineArguments.kt#L83
        val argString = args.joinToString(" ") { arg ->
            if (arg.contains(" ") || arg.contains("'")) {
                "'${arg.replace("\\", "\\\\").replace("'", "\\'")}'"
            } else {
                arg
            }
        }

        tempRoot.path.createDirectories()
        val argFile = Files.createTempFile(tempRoot.path, "kotlin-args-", ".txt")
        return try {
            argFile.writeText(argString)
            block(argFile)
        } finally {
            BuildPrimitives.deleteLater(argFile)
        }
    }

    private const val MAVEN_CENTRAL_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"
    private const val KOTLIN_IDE_PLUGIN_DEPENDENCIES_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
    private const val ARTIFACT_GROUP_ID = "org.jetbrains.kotlin"
}
