/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.sdk.resources.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.amper.buildinfo.AmperBuild
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/**
 * Utilities to share the OpenTelemetry context between the main process and subprocesses.
 */
object ChildProcessTelemetry {

    @Serializable
    private class OtelSharedContext(val contextMap: Map<String, String>)

    /**
     * This system environment variable is used to propagate the OpenTelemetry context from the parent process to the child processes.
     */
    const val OTEL_PARENT_CONTEXT_ENV_VAR = "AMPER_OTEL_PARENT_CONTEXT"

    /**
     * The folder on disk where the telemetry data should be stored.
     */
    const val OTEL_FOLDER_ENV_VAR = "AMPER_OTEL_FOLDER"

    /**
     * Sets up an OpenTelemetry environment for a child process with telemetry tracing.
     *
     * @param processName the name of the child process. It will be used for the name of the root span of the process,
     * as the telemetry service name, and as a prefix of traces' filename.
     */
    suspend fun <T> withChildProcessTelemetrySpan(processName: String, block: suspend CoroutineScope.() -> T): T {
        val telemetryFolder = getTelemetryFolderFromEnvironmentVariable()
        val fileName = uniqueFileName(processName)
        val telemetryFile = telemetryFolder.createDirectories() / fileName
        check(!telemetryFile.exists()) { "Telemetry file $telemetryFile should not exist" }

        val openTelemetry = TelemetrySetup.createOpenTelemetry(
            telemetryFile.outputStream(), telemetryResource(processName))
        GlobalOpenTelemetry.set(openTelemetry)
        TelemetrySetup.closeTelemetryOnShutdown(openTelemetry) { error ->
            LoggerFactory.getLogger(javaClass).error("Exception on shutdown: ${error.message}", error)
        }

        val parentProcessContext = openTelemetry.extractContextFromEnvironmentVariable()

        return spanBuilder(processName).setParent(parentProcessContext).use {
            block()
        }
    }

    /**
     * Collects the current Context of the GlobalOpenTelemetry and serializes it to a string.
     */
    fun createSerializedParentContextData(): String {
        val contextData = OtelSharedContext(createParentContextMap())
        return Json.encodeToString<OtelSharedContext>(contextData)
    }

    private fun createParentContextMap(): Map<String, String> {
        val propagator = GlobalOpenTelemetry.getPropagators().textMapPropagator
        val dataMap = mutableMapOf<String, String>()
        propagator.inject(Context.current(), dataMap) { carrier, key, value ->
            if (carrier != null) carrier[key] = value
        }
        return dataMap
    }

    /**
     * Extracts the OpenTelemetry Context from the [environment variable](OTEL_PARENT_CONTEXT_ENV_VAR).
     * Returns null if the variable is not set or its value cannot be parsed.
     *
     * The returned context should be set as the [parent context](SpanBuilder.setParent)
     * to inline the child process telemetry into the telemetry of the parent process.
     */
    private fun OpenTelemetry.extractContextFromEnvironmentVariable(): Context {
        val envValue = System.getenv(OTEL_PARENT_CONTEXT_ENV_VAR)
            ?: error("Environment variable $OTEL_PARENT_CONTEXT_ENV_VAR is not set")
        val data = Json.decodeFromString<OtelSharedContext>(envValue)
        return extractContextFromMap(data.contextMap)
    }

    private fun OpenTelemetry.extractContextFromMap(contextData: Map<String, String>): Context {
        val propagator = this.propagators.textMapPropagator
        return propagator.extract(
            Context.root(), contextData,
            object : TextMapGetter<Map<String, String>> {
                override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys

                override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
            }
        )
    }

    private fun getTelemetryFolderFromEnvironmentVariable(): Path {
        val env = System.getenv(OTEL_FOLDER_ENV_VAR)
            ?: error("Telemetry environment variable $OTEL_FOLDER_ENV_VAR is not set")
        val path = Path(env)
        return if (path.exists()) path else error("Telemetry folder does not exist: $path")
    }

    private fun telemetryResource(serviceName: String): Resource = Resource.getDefault().merge(
        Resource.builder()
            .put("service.name", serviceName)
            .put("service.namespace", "org.jetbrains.amper")
            .put("service.instance.id", UUID.randomUUID().toString())
            .put("service.version", AmperBuild.mavenVersion)
            .put("os.type", System.getProperty("os.name"))
            .put("os.version", System.getProperty("os.version"))
            .put("host.arch", System.getProperty("os.arch"))
            .build()
    )

    private fun uniqueFileName(processName: String): String {
        val pid = ProcessHandle.current().pid() // avoid clashes with concurrent Amper processes
        return "${processName}_$pid.jsonl"
    }
}