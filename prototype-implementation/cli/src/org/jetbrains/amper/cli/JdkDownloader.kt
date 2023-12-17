/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.ExtractOptions
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

// TODO so far, no version selection, need to design it a little

object JdkDownloader {
    const val JBR_SDK_VERSION = "17.0.9b1109.1"
    private val LOG = LoggerFactory.getLogger(javaClass)

    suspend fun getJdkHome(userCacheRoot: AmperUserCacheRoot): Path {
        val os = OS.current
        val arch = Arch.current
        return getJdkHome(userCacheRoot, os, arch) {
            LOG.info(it)
        }
    }

    private suspend fun getJdkHome(userCacheRoot: AmperUserCacheRoot, os: OS, arch: Arch, infoLog: (String) -> Unit): Path {
        val jdkUrl = getUrl(os, arch).toString()
        val jdkArchive = Downloader.downloadFileToCacheLocation(jdkUrl, userCacheRoot)
        val jdkExtracted = extractFileToCacheLocation(
            jdkArchive, userCacheRoot, ExtractOptions.STRIP_ROOT)
        infoLog("jps-bootstrap JDK is at $jdkExtracted")

        val jdkHome: Path = if (os == OS.MACOSX) {
            jdkExtracted.resolve("Contents").resolve("Home")
        }
        else {
            jdkExtracted
        }
        val executable = getJavaExecutable(jdkHome)
        infoLog("JDK home is at $jdkHome, executable at $executable")
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

    private fun getUrl(os: OS, arch: Arch): URI {
        val ext = ".tar.gz"
        val osString: String = when (os) {
            OS.WINDOWS -> "windows"
            OS.MACOSX -> "osx"
            OS.LINUX -> "linux"
        }
        val archString: String = when (arch) {
            Arch.X86_64 -> "x64"
            Arch.ARM64 -> "aarch64"
        }

        val jdkBuild = JBR_SDK_VERSION

        val jdkBuildSplit = jdkBuild.split("b".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        check(jdkBuildSplit.size == 2) { "Malformed jdkBuild property: $jdkBuild" }
        val version = jdkBuildSplit[0]
        val build = "b" + jdkBuildSplit[1]
        return URI.create("https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-" +
                version + "-" + osString + "-" +
                archString + "-" + build + ext)
    }

    private enum class OS {
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

    private enum class Arch {
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
