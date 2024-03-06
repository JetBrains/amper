/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tools

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.intellij.CommandLineUtils
import kotlin.io.path.name
import kotlin.io.path.pathString

object JaegerTool: Tool {
    override val name: String
        get() = "jaeger"

    override fun run(args: List<String>, userCacheRoot: AmperUserCacheRoot) {
        val os = DefaultSystemInfo.detect()
        val osString = when (os.family) {
            SystemInfo.OsFamily.Windows -> "windows"
            SystemInfo.OsFamily.Linux -> "linux"
            SystemInfo.OsFamily.MacOs -> "darwin"
            else -> error("Unsupported OS: ${os.family}")
        }
        val archString = when (os.arch) {
            SystemInfo.Arch.X64 -> "amd64"
            SystemInfo.Arch.Arm64 -> "arm64"
        }

        val url = "https://github.com/jaegertracing/jaeger/releases/download/v$VERSION/jaeger-${VERSION}-$osString-$archString.tar.gz"
        runBlocking {
            val file = Downloader.downloadFileToCacheLocation(url, userCacheRoot)
            val root = extractFileToCacheLocation(file, userCacheRoot, ExtractOptions.STRIP_ROOT)

            val executable = root.resolve("jaeger-all-in-one${if (os.family == SystemInfo.OsFamily.Windows) ".exe" else ""}")
            val cmd = listOf(executable.pathString) + args

            DeadLockMonitor.disable()

            val process = ProcessBuilder(CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd))
                .inheritIO()
                .start()
            val exitValue = process.onExit().await().exitValue()
            println("${executable.name} exited with code $exitValue")
        }
    }

    private const val VERSION = "1.54.0"
}
