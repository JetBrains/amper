/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.downloader

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path

const val MAVEN_CENTRAL_REPOSITORY_URL = "https://repo1.maven.org/maven2"

const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"

/**
 * Downloads and extracts current system specific kotlin native.
 * Returns null if kotlin native is not supported on current system/arch.
 */
@UsedInIdePlugin
suspend fun downloadAndExtractKotlinNative(
    version: String,
    userCacheRoot: AmperUserCacheRoot,
): Path? {
    // TODO should be forward-compatible in some way,
    //  i.e. support new os/arch combinations if future kotlin version support them
    //  probably the easiest way is to peek maven central page (could be cached!)
    //  https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-native/1.9.22/
    val packaging: String = when {
        OsFamily.current.isMac || OsFamily.current.isLinux -> "tar.gz"
        OsFamily.current.isWindows -> "zip"
        else -> null
    } ?: return null

    val classifier: String = when (OsFamily.current) {
        OsFamily.MacOs -> when (Arch.current) {
            Arch.X64 -> "macos-x86_64"
            Arch.Arm64 -> "macos-aarch64"
        }

        OsFamily.Windows -> when (Arch.current) {
            Arch.X64 -> "windows-x86_64"
            else -> null
        }

        OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> when (Arch.current) {
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
        userCacheRoot = userCacheRoot,
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
    userCacheRoot: AmperUserCacheRoot,
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
