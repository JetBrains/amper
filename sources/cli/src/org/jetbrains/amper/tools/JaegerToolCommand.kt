/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tools

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.terminal.Terminal
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.RootCommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.downloader.extractFileToCacheLocation
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.awaitAndGetAllOutput
import java.net.Socket
import kotlin.io.path.name
import kotlin.io.path.pathString

class JaegerToolCommand: SuspendingCliktCommand(name = "jaeger") {
    private val openBrowser by option(
        "--open-browser",
        help = "Open Jaeger UI in browser if Jaeger successfully starts",
    ).boolean().default(true)

    private val port by option("--jaeger-port", help = "The HTTP port to use for the Jaeger UI")
        .int().default(16686)

    private val version by option("--jaeger-version", help = "The version of Jaeger to download and run")
        .default("1.61.0")

    private val jaegerArguments by argument(name = "jaeger arguments").multiple()

    private val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Download and run Jaeger server https://www.jaegertracing.io"

    override fun helpEpilog(context: Context): String = "Use -- to separate Jaeger's arguments from Amper options"

    override suspend fun run() {
        val userCacheRoot = commonOptions.sharedCachesRoot
        val terminal = commonOptions.terminal

        val os = DefaultSystemInfo.detect()
        val osString = when (os.family) {
            OsFamily.Windows -> "windows"
            OsFamily.Linux -> "linux"
            OsFamily.MacOs -> "darwin"
            else -> error("Unsupported OS: ${os.family}")
        }
        val archString = when (os.arch) {
            Arch.X64 -> "amd64"
            Arch.Arm64 -> "arm64"
        }

        // if port is not available, jaeger will report it by itself
        val checkForHttpPortAvailability = openBrowser && !connectToLocalPort(port)

        val jaegerDistUrl = "https://github.com/jaegertracing/jaeger/releases/download/v$version/jaeger-${version}-$osString-$archString.tar.gz"
        withContext(Dispatchers.IO) {
            val file = Downloader.downloadFileToCacheLocation(jaegerDistUrl, userCacheRoot)
            val root = extractFileToCacheLocation(file, userCacheRoot, ExtractOptions.STRIP_ROOT)

            val executable = root.resolve("jaeger-all-in-one${if (os.family == OsFamily.Windows) ".exe" else ""}")
            val cmd = listOf(executable.pathString) + jaegerArguments

            DeadLockMonitor.disable()

            val process = ProcessBuilder(CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd))
                .start()

            if (checkForHttpPortAvailability) {
                launch {
                    while (true) {
                        if (!process.isAlive) {
                            break
                        }

                        if (connectToLocalPort(port)) {
                            val url = "http://127.0.0.1:$port"
                            terminal.println("*** Opening browser $url *** (specify --open-browser=false to disable)")
                            openBrowser(url, terminal)
                            break
                        }

                        delay(10)
                    }
                }
            }

            val result = process.awaitAndGetAllOutput(PrintToTerminalProcessOutputListener(terminal))

            if (result.exitCode != 0) {
                userReadableError("${executable.name} exited with code ${result.exitCode}")
            }
        }
    }

    private suspend fun openBrowser(url: String, t: Terminal) {
        val cmd = when {
            OsFamily.current.isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            OsFamily.current.isLinux -> listOf("xdg-open", url)
            OsFamily.current.isMac -> listOf("open", url)
            else -> return
        }

        t.println("Starting $cmd")

        val process = runInterruptible {
            ProcessBuilder(cmd).inheritIO().start()
        }

        val exitCode = process.awaitExit()
        if (exitCode != 0) {
            t.println("$cmd failed with exit code $exitCode")
        }
    }

    @Suppress("SameParameterValue")
    private fun connectToLocalPort(port: Int): Boolean = try {
        Socket("127.0.0.1", port).use { socket -> socket.isConnected }
    } catch (_: Throwable) {
        false
    }
}
