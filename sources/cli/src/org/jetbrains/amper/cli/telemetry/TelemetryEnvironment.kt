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
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.unwrap
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream

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

    fun setup() {
        val cacheRoot = AmperUserCacheRoot.fromCurrentUserResult().unwrap()
        val initialTracesPath = userLevelTracesPath(cacheRoot)
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

/**
 * An output stream to a file that can be moved concurrently with the writes.
 *
 * The stream initially writes to [initialPath], and then [moveTo] can be used to change the path.
 */
private class MovableFileOutputStream(initialPath: Path) : OutputStream() {

    private var currentPath = initialPath
    private var fileStream = initialPath.outputStream().buffered()

    /**
     * Moves the destination file of this [MovableFileOutputStream] to the given [newPath].
     * This is not just a path change: when this method is called, the write operations are temporarily blocked while
     * the file is being physically moved to the new location.
     * The subsequent write operations will append to the file in the new location.
     */
    @Synchronized
    fun moveTo(newPath: Path) {
        if (newPath == currentPath) {
            return
        }
        try {
            fileStream.flush()
        } finally {
            fileStream.close()
        }
        // if nothing has been written so far, the file might not exist at all
        if (currentPath.exists()) {
            currentPath.moveTo(newPath)
        }
        currentPath = newPath
        fileStream = newPath.outputStream(WRITE, CREATE, APPEND).buffered()
    }

    @Synchronized
    override fun write(b: Int) {
        fileStream.write(b)
    }

    @Synchronized
    override fun write(b: ByteArray) {
        fileStream.write(b)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        fileStream.write(b, off, len)
    }

    @Synchronized
    override fun flush() {
        fileStream.flush()
    }

    @Synchronized
    override fun close() {
        fileStream.close()
    }
}
