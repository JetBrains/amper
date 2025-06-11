/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.green
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.CliProblemReporterContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.createProjectContext
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.core.extract.ExtractOptions
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import org.jetbrains.amper.telemetry.use
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.streams.asSequence

internal class JaegerToolCommand : AmperSubcommand(name = "jaeger") {

    private val openBrowser by option(
        "--open-browser",
        help = "Open Jaeger UI in browser if Jaeger successfully starts",
    ).boolean().default(true)

    private val autoImportTraces by option(
        "--auto-import-traces",
        help = "Automatically import traces from the local file system",
    ).boolean().default(true)

    private val port by option("--jaeger-port", help = "The HTTP port to use for the Jaeger UI")
        .int().default(16686)

    private val version by option("--jaeger-version", help = "The version of Jaeger to download and run")
        .default("1.64.0")

    private val jaegerArguments by argument(name = "jaeger_arguments").multiple()

    override fun help(context: Context): String = "Download and run Jaeger server https://www.jaegertracing.io"

    override fun helpEpilog(context: Context): String = "Use `--` to separate Jaeger's arguments from Amper options"

    override suspend fun run() {
        val userCacheRoot = commonOptions.sharedCachesRoot

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

        withContext(Dispatchers.IO) {
            // if the port is already in use jaeger will report it by itself and fail (and we should not open the browser)
            val shouldAutoOpenBrowser = openBrowser && !jaegerPortIsReady(port)

            val jaegerDistUrl = "https://github.com/jaegertracing/jaeger/releases/download/v$version/jaeger-${version}-$osString-$archString.tar.gz"
            val file = Downloader.downloadFileToCacheLocation(jaegerDistUrl, userCacheRoot)
            val root = extractFileToCacheLocation(file, userCacheRoot, ExtractOptions.STRIP_ROOT)

            val executable = root.resolve("jaeger-all-in-one${if (os.family == OsFamily.Windows) ".exe" else ""}")
            val cmd = listOf(executable.pathString) + jaegerArguments

            DeadLockMonitor.disable()

            coroutineScope {
                launch {
                    if (autoImportTraces) {
                       importTraces()
                    }
                    if (shouldAutoOpenBrowser) {
                        autoOpenBrowserWhenReady()
                    }
                }


                val exitCode = runProcess(
                    command = CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd),
                    outputListener = PrintToTerminalProcessOutputListener(terminal),
                )
                if (exitCode != 0) {
                    userReadableError("${executable.name} exited with code $exitCode")
                }
            }
        }
    }

    private suspend fun importTraces() = coroutineScope {
        awaitJaegerPortReady()

        val traceFiles = findTraceFiles()
        if (traceFiles.isEmpty()) {
            terminal.println("No trace files found, do nothing")
            return@coroutineScope
        }

        terminal.println("Found ${traceFiles.size} trace file(s), sending them to Jaeger")

        for (file in traceFiles) {
            val jsonLines = Files.readAllLines(file)
            launch { sendTracesToJaeger(jsonLines) }
        }
    }

    private suspend fun findTraceFiles(): List<Path> {
        val result = mutableListOf<Path>()

        val buildLogsDir = commonOptions.buildOutputRoot?.resolve("logs") ?: run {
            val amperProjectContext = try {
                spanBuilder("Create Amper project context").use {
                    with(CliProblemReporterContext) {
                        createProjectContext(commonOptions.explicitRoot?.toAbsolutePath())
                    }
                }
            } catch (_: UserReadableError) {
                null
            }
            amperProjectContext
                ?.projectRootDir
                ?.toNioPath()
                ?.resolve("build")
                ?.resolve("logs")
        }

        if (buildLogsDir?.exists() == true) {
            Files.walk(buildLogsDir)
                .asSequence()
                .filter { it.isRegularFile() && it.name.endsWith("opentelemetry_traces.jsonl") }
                .forEach { result.add(it) }
        }
        return result
    }

    private suspend fun sendTracesToJaeger(jsonLines: List<String>) {
        awaitJaegerPortReady()
        val otlpUrl = "http://127.0.0.1:4318/v1/traces"  // Standard OTLP HTTP port
        HttpClient().use { client ->
            for (line in jsonLines) {
                if (line.isBlank()) continue

                val response = client.post(otlpUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(line)
                }
                if (response.status.value !in 200..299) {
                    terminal.println("Error sending trace to OTLP endpoint: ${response.status.value} - ${response.bodyAsText()}")
                }
            }
        }
    }

    private suspend fun autoOpenBrowserWhenReady() {
        awaitJaegerPortReady()
        val url = "http://127.0.0.1:$port"
        terminal.println("${green("*** Opening browser $url ***")} (specify --open-browser=false to disable)")
        openBrowser(url)
    }

    private suspend fun openBrowser(url: String) {
        val cmd = when {
            OsFamily.current.isWindows -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            OsFamily.current.isLinux -> listOf("xdg-open", url)
            OsFamily.current.isMac -> listOf("open", url)
            else -> return
        }

        terminal.println("Starting $cmd")

        val exitCode = runProcessWithInheritedIO(command = cmd)
        if (exitCode != 0) {
            terminal.println("$cmd failed with exit code $exitCode")
        }
    }

    private suspend fun awaitJaegerPortReady(shouldKeepTrying: () -> Boolean = { true }) {
        while (!jaegerPortIsReady(port) && shouldKeepTrying()) {
            delay(10)
        }
    }

    private fun jaegerPortIsReady(port: Int): Boolean = try {
        Socket("127.0.0.1", port).use { socket -> socket.isConnected }
    } catch (_: Throwable) {
        false
    }
}
