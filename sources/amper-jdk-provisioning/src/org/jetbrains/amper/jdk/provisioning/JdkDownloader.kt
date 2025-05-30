/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.OsFamily
import java.net.URL
import kotlin.io.path.isDirectory

// TODO so far, no version selection, need to design it a little

object JdkDownloader {

    // private because we don't want other classes to use these versions, they're an implementation detail right now
    private const val correttoJdkVersion = "21.0.1.12.1"
    private const val microsoftJdkVersion = "21.0.1"
    private const val jbrJdkVersion = "21.0.5"
    private const val jbrBuild = "b631.30"

    suspend fun getJdk(userCacheRoot: AmperUserCacheRoot): Jdk = getJdk(userCacheRoot, OsFamily.current, Arch.current)

    internal suspend fun getJdk(userCacheRoot: AmperUserCacheRoot, os: OsFamily, arch: Arch): Jdk {
        val version = hardcodedVersionFor(os, arch)
        return downloadJdk(
            downloadUri = jdkDownloadUrlFor(os, arch, version),
            userCacheRoot = userCacheRoot,
            version = version,
        )
    }

    suspend fun getJbr(userCacheRoot: AmperUserCacheRoot): Jdk = getJbr(userCacheRoot, OsFamily.current, Arch.current)

    internal suspend fun getJbr(userCacheRoot: AmperUserCacheRoot, os: OsFamily, arch: Arch): Jdk = downloadJdk(
        downloadUri = jbrJdkUrl(os, arch, jbrJdkVersion, jbrBuild),
        userCacheRoot = userCacheRoot,
        version = jbrJdkVersion,
    )

    private suspend fun downloadJdk(downloadUri: URL, userCacheRoot: AmperUserCacheRoot, version: String): Jdk {
        val jdkArchive = Downloader.downloadFileToCacheLocation(downloadUri.toString(), userCacheRoot)
        val extractedJdkRoot = extractFileToCacheLocation(jdkArchive, userCacheRoot, ExtractOptions.STRIP_ROOT)
        // Some archives for macOS contain the JDK under amazon-corretto-X.jdk/Contents/Home
        val contentsHome = extractedJdkRoot.resolve("Contents/Home")
        val jdkHome = if (contentsHome.isDirectory()) contentsHome else extractedJdkRoot
        return Jdk(homeDir = jdkHome, downloadUrl = downloadUri, version = version)
    }

    private fun jdkDownloadUrlFor(os: OsFamily, arch: Arch, version: String): URL =
        // No Corretto build for Windows Arm64, so use Microsoft's JDK
        if (os == OsFamily.Windows && arch == Arch.Arm64) {
            microsoftJdkUrlWindowsArm64(version)
        } else {
            correttoJdkUrl(os, arch, version)
        }

    private fun hardcodedVersionFor(os: OsFamily, arch: Arch) = if (os == OsFamily.Windows && arch == Arch.Arm64) {
        microsoftJdkVersion
    } else {
        correttoJdkVersion
    }

    // See releases: https://learn.microsoft.com/en-ca/java/openjdk/download
    private fun microsoftJdkUrlWindowsArm64(version: String): URL =
        URL("https://aka.ms/download-jdk/microsoft-jdk-$version-windows-aarch64.zip")

    // See releases: https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html
    private fun correttoJdkUrl(os: OsFamily, arch: Arch, version: String): URL {
        val ext = if (os == OsFamily.Windows) "-jdk.zip" else ".tar.gz"
        val osString: String = os.forCorrettoUrl()
        val archString: String = arch.forUrl()
        return URL("https://corretto.aws/downloads/resources/$version/amazon-corretto-$version-$osString-$archString$ext")
    }

    private fun jbrJdkUrl(os: OsFamily, arch: Arch, version: String, build: String): URL {
        val osString: String = os.forJbrUrl()
        val archString: String = arch.forUrl()
        return URL("https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-$version-$osString-$archString-$build.tar.gz")
    }
}

private fun Arch.forUrl() = when (this) {
    Arch.X64 -> "x64"
    Arch.Arm64 -> "aarch64"
}

private fun OsFamily.forCorrettoUrl() = when(this) {
    OsFamily.Windows -> "windows"
    OsFamily.MacOs -> "macosx"
    OsFamily.Linux -> "linux"
    OsFamily.FreeBSD -> "linux"
    OsFamily.Solaris -> "linux"
}

private fun OsFamily.forJbrUrl() = when(this) {
    OsFamily.Windows -> "windows"
    OsFamily.MacOs -> "osx"
    OsFamily.Linux -> "linux"
    OsFamily.FreeBSD -> "linux"
    OsFamily.Solaris -> "linux"
}
