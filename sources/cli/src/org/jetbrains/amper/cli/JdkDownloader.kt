/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.ExtractOptions
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

// TODO so far, no version selection, need to design it a little

object JdkDownloader {
    suspend fun getJdkHome(userCacheRoot: AmperUserCacheRoot): Path {
        return getJdkHome(userCacheRoot, currentSystemFixedJdkUrl)
    }

    internal suspend fun getJdkHome(userCacheRoot: AmperUserCacheRoot, jdkUrl: URI): Path {
        val jdkArchive = Downloader.downloadFileToCacheLocation(jdkUrl.toString(), userCacheRoot)
        val jdkExtracted = extractFileToCacheLocation(
            jdkArchive, userCacheRoot, ExtractOptions.STRIP_ROOT)

        val contentsHome = jdkExtracted.resolve("Contents").resolve("Home")
        val jdkHome: Path = if (contentsHome.isDirectory()) contentsHome else jdkExtracted

        // assert that executable could be indeed found under jdkHome
        getJavaExecutable(jdkHome)

        return jdkHome
    }

    private fun getExecutable(jdkHome: Path, executableName: String): Path {
        for (candidateRelative in mutableListOf("bin/$executableName", "bin/$executableName.exe")) {
            val candidate = jdkHome.resolve(candidateRelative)
            if (Files.exists(candidate)) {
                return candidate
            }
        }
        throw IllegalStateException("No $executableName executables were found under $jdkHome")
    }

    fun getJavaExecutable(jdkHome: Path): Path = getExecutable(jdkHome, "java")
    fun getJavacExecutable(jdkHome: Path): Path = getExecutable(jdkHome, "javac")

    val currentSystemFixedJdkUrl: URI by lazy {
        getUrl(OS.current, Arch.current)
    }

    internal fun getUrl(os: OS, arch: Arch): URI {
        if (os == OS.WINDOWS && arch == Arch.ARM64) {
            // no corretto build for windows arm64, use microsoft jdk
            return URI.create("https://aka.ms/download-jdk/microsoft-jdk-21.0.1-windows-aarch64.zip")
        }

        val ext = if (os == OS.WINDOWS) "-jdk.zip" else ".tar.gz"
        val osString: String = when (os) {
            OS.WINDOWS -> "windows"
            OS.MACOSX -> "macosx"
            OS.LINUX -> "linux"
        }
        val archString: String = when (arch) {
            Arch.X86_64 -> "x64"
            Arch.ARM64 -> "aarch64"
        }

        // not a global constant, so other classes can't reference it, it's an implementation detail now
        val correttoVersion = "21.0.1.12.1"
        return URI.create("https://corretto.aws/downloads/resources/$correttoVersion/amazon-corretto-$correttoVersion-$osString-$archString$ext")
    }

    internal enum class OS {
        WINDOWS,
        MACOSX,
        LINUX;

        companion object {
            val current: OS
                get() {
                    val osName = System.getProperty("os.name").lowercase()
                    return when {
                        osName.startsWith("mac") -> MACOSX
                        osName.startsWith("linux") -> LINUX
                        osName.startsWith("windows") -> WINDOWS
                        else -> throw IllegalStateException("Only Mac/Linux/Windows are supported now, current os: $osName")
                    }
                }
        }
    }

    internal enum class Arch {
        X86_64,
        ARM64;

        companion object {
            val current: Arch
                get() {
                    val arch = System.getProperty("os.arch").lowercase()
                    if ("x86_64" == arch || "amd64" == arch) return X86_64
                    if ("aarch64" == arch || "arm64" == arch) return ARM64
                    throw IllegalStateException("Only X86_64 and ARM64 are supported, current arch: $arch")
                }
        }
    }
}
