/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.resources.Resource
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.telemetry.TelemetrySetup
import org.jetbrains.amper.util.DateTimeFormatForFilenames
import org.jetbrains.amper.util.nowInDefaultTimezone
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
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
        val datetime = LocalDateTime.nowInDefaultTimezone().format(DateTimeFormatForFilenames)
        val pid = ProcessHandle.current().pid() // avoid clashes with concurrent Amper processes
        val workingDirName = Path(".").absolute().normalize().name
        "opentelemetry_traces_${datetime}_${pid}_${workingDirName.take(20)}.jsonl"
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
        moveSpansFile(newPath = amperBuildLogsRoot.telemetryPath.createDirectories() / "amper_cli_traces.jsonl")
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
        val outputStream = MovableFileOutputStream(initialPath = userLevelTracesPath(defaultCacheRoot))
        movableFileOutputStream = outputStream
        val openTelemetry = TelemetrySetup.createOpenTelemetry(outputStream, resource)
        GlobalOpenTelemetry.set(openTelemetry)
        TelemetrySetup.closeTelemetryOnShutdown(openTelemetry) { error ->
            LoggerFactory.getLogger(javaClass).error("Exception on shutdown: ${error.message}", error)
        }
    }
}
