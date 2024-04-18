/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.downloader.Downloader
import org.jetbrains.amper.downloader.extractFileToCacheLocation
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.processes.awaitAndGetAllOutput
import org.jetbrains.amper.util.OS
import java.net.Socket
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.name
import kotlin.io.path.pathString

class JaegerToolCommand: CliktCommand(
    name = "jaeger",
    epilog = "Use -- to separate tool's arguments from Amper options"
) {
    private val openBrowser by option(
        "--open-browser",
        help = "Open Jaeger UI in browser if Jaeger successfully listens on HTTP port $JAEGER_HTTP_PORT",
    ).boolean().default(true)

    private val toolArguments by argument(name = "tool arguments").multiple()
    private val projectContext by requireObject<Lazy<ProjectContext>>()

    override fun run() {
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

        // if port is not available, jaeger will report it by itself
        val checkForHttpPortAvailability = openBrowser && !connectToLocalPort(JAEGER_HTTP_PORT)

        val jaegerDistUrl = "https://github.com/jaegertracing/jaeger/releases/download/v$VERSION/jaeger-${VERSION}-$osString-$archString.tar.gz"
        runBlocking(Dispatchers.IO) {
            val file = Downloader.downloadFileToCacheLocation(jaegerDistUrl, projectContext.value.userCacheRoot)
            val root = extractFileToCacheLocation(file, projectContext.value.userCacheRoot, ExtractOptions.STRIP_ROOT)

            val executable = root.resolve("jaeger-all-in-one${if (os.family == SystemInfo.OsFamily.Windows) ".exe" else ""}")
            val cmd = listOf(executable.pathString) + toolArguments

            DeadLockMonitor.disable()

            val process = ProcessBuilder(CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd))
                .start()

            if (checkForHttpPortAvailability) {
                launch {
                    while (true) {
                        if (!process.isAlive) {
                            break
                        }

                        if (connectToLocalPort(JAEGER_HTTP_PORT)) {
                            val url = "http://127.0.0.1:$JAEGER_HTTP_PORT"
                            println("*** Opening browser $url *** (specify --open-browser=false to disable)")
                            openBrowser(url)
                            break
                        }

                        delay(10)
                    }
                }
            }

            val result = process.awaitAndGetAllOutput(
                onStdoutLine = { line ->
                    println(line)
                },
                onStderrLine = { line ->
                    System.err.println(line)
                }
            )

            println("${executable.name} exited with code ${result.exitCode}")
        }
    }

    private suspend fun openBrowser(url: String) {
        val cmd = when {
            OS.isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            OS.isLinux -> listOf("xdg-open", url)
            OS.isMac -> listOf("open", url)
            else -> return
        }

        println("Starting $cmd")

        val process = runInterruptible {
            ProcessBuilder(cmd).inheritIO().start()
        }

        val exitCode = process.awaitExit()
        if (exitCode != 0) {
            println("$cmd failed with exit code $exitCode")
        }
    }

    @Suppress("SameParameterValue")
    private fun connectToLocalPort(port: Int): Boolean = try {
        Socket("127.0.0.1", port).use { socket -> socket.isConnected }
    } catch (_: Throwable) {
        false
    }

    companion object {
        private const val JAEGER_HTTP_PORT = 16686
        private const val VERSION = "1.56.0"
    }
}
