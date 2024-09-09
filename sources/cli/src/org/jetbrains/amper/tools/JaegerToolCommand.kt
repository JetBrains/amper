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
import com.github.ajalt.mordant.terminal.Terminal
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.jetbrains.amper.cli.RootCommand
import org.jetbrains.amper.cli.getUserCacheRoot
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

class JaegerToolCommand: CliktCommand(
    name = "jaeger",
    help = "Download and run Jaeger server https://www.jaegertracing.io",
    epilog = "Use -- to separate tool's arguments from Amper options",
) {
    private val openBrowser by option(
        "--open-browser",
        help = "Open Jaeger UI in browser if Jaeger successfully listens on HTTP port $JAEGER_HTTP_PORT",
    ).boolean().default(true)

    private val toolArguments by argument(name = "tool arguments").multiple()

    private val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun run() {
        val userCacheRoot = getUserCacheRoot(commonOptions)
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
        val checkForHttpPortAvailability = openBrowser && !connectToLocalPort(JAEGER_HTTP_PORT)

        val jaegerDistUrl = "https://github.com/jaegertracing/jaeger/releases/download/v$VERSION/jaeger-${VERSION}-$osString-$archString.tar.gz"
        runBlocking(Dispatchers.IO) {
            val file = Downloader.downloadFileToCacheLocation(jaegerDistUrl, userCacheRoot)
            val root = extractFileToCacheLocation(file, userCacheRoot, ExtractOptions.STRIP_ROOT)

            val executable = root.resolve("jaeger-all-in-one${if (os.family == OsFamily.Windows) ".exe" else ""}")
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

    companion object {
        private const val JAEGER_HTTP_PORT = 16686
        private const val VERSION = "1.56.0"
    }
}
