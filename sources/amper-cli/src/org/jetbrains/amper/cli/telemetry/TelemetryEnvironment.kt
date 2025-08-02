/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.exporter.logging.otlp.internal.traces.OtlpStdoutSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.name

object TelemetryEnvironment {

    /**
     * The filename to use for the traces file when placed in the user-level Amper cache.
     * It has to be unique, and somehow convey which project/command it came from.
     */
    private val userLevelTracesFilename: String by lazy {
        val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss_SSS"))
        val workingDirName = Path(".").absolute().normalize().name
        "opentelemetry_traces_${datetime}_${workingDirName.take(20)}.jsonl"
    }

    private var movableFileOutputStream: MovableFileOutputStream? = null

    // Some standard attributes from https://opentelemetry.io/docs/specs/semconv/resource/
    private val resource: Resource = Resource.getDefault().merge(
        Resource.builder()
            .put("service.name", "Amper")
            .put("service.namespace", "org.jetbrains.amper")
            .put("service.instance.id", UUID.randomUUID().toString())
            .put("service.version", AmperBuild.mavenVersion)
            .put("os.type", System.getProperty("os.name"))
            .put("os.version", System.getProperty("os.version"))
            .put("host.arch", System.getProperty("os.arch"))
            .build()
    )

    fun setUserCacheRoot(amperUserCacheRoot: AmperUserCacheRoot) {
        moveSpansFile(newPath = userLevelTracesPath(amperUserCacheRoot))
    }

    fun setLogsRootDirectory(amperBuildLogsRoot: AmperBuildLogsRoot) {
        moveSpansFile(newPath = amperBuildLogsRoot.path.createDirectories() / "opentelemetry_traces.jsonl")
    }

    private fun userLevelTracesPath(userCacheRoot: AmperUserCacheRoot): Path {
        val userLevelTelemetryDir = (userCacheRoot.path / "telemetry").createDirectories()
        return userLevelTelemetryDir / userLevelTracesFilename
    }

    private fun moveSpansFile(newPath: Path) {
        movableFileOutputStream?.moveTo(newPath)
            ?: error("Initial path for traces was not set. TelemetryEnvironment.setup() must be called first.")
    }

    fun setup(defaultCacheRoot: AmperUserCacheRoot) {
        val initialTracesPath = userLevelTracesPath(defaultCacheRoot)
        movableFileOutputStream = MovableFileOutputStream(initialPath = initialTracesPath)

        val exporter = OtlpStdoutSpanExporter.builder()
            .setOutput(movableFileOutputStream)
            .setWrapperJsonObject(true)
            .build()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
        GlobalOpenTelemetry.set(openTelemetry)

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            try {
                openTelemetry.close()
            } catch (t: Throwable) {
                LoggerFactory.getLogger(javaClass).error("Exception on shutdown: ${t.message}", t)
            }
        })
    }
}
