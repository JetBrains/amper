/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.routing.route
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.telemetry.CustomListenerSpanProcessor
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.TaskName

@Serializable
data class Task(val taskHierarchy: List<String>)

@Serializable
data class SerializableSpan(
    val name: String,
    val traceId: String,
    val spanId: String,
    val parentSpanId: String,
    val startTimeNanos: Long,
    val endTimeNanos: Long,
    val attributes: Map<String, String>,
    val status: String,
    val events: List<SerializableEvent>
)

@Serializable
data class SerializableEvent(
    val name: String,
    val epochNanos: Long,
    val attributes: Map<String, String>
)

fun ReadableSpan.toSerializable(): SerializableSpan {
    val spanData = toSpanData()
    return SerializableSpan(
        name = spanData.name,
        traceId = spanData.traceId.toString(),
        spanId = spanData.spanId.toString(),
        parentSpanId = spanData.parentSpanId.toString(),
        startTimeNanos = spanData.startEpochNanos,
        endTimeNanos = spanData.endEpochNanos,
        attributes = spanData.attributes.asMap().entries.associate { entry ->
            entry.key.toString() to entry.value.toString()
        },
        status = spanData.status.toString(),
        events = spanData.events.map { event ->
            SerializableEvent(
                name = event.name,
                epochNanos = event.epochNanos,
                attributes = event.attributes.asMap().entries.associate { entry ->
                    entry.key.toString() to entry.value.toString()
                }
            )
        }
    )
}

private const val runTasksSpanName = "Run tasks"

private const val defaultPort = 8000

internal class ServerCommand : AmperSubcommand(name = "server") {

    override val hiddenFromHelp: Boolean = true

    private val port by option("-p", "--port", help = "The port to listen on")
        .int(acceptsValueWithoutName = true)
        .default(defaultPort)

    override fun help(context: Context): String =
        "Start a server that accepts tasks from Amper and runs them. The server runs on port $defaultPort by default."

    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            embeddedServer(Netty, port = port) {
                install(ContentNegotiation) { json() }
                install(SSE)
                routing {
                    route("/task", HttpMethod.Post) {
                        sse {
                            val channel = Channel<ReadableSpan>(1024)
                            val listener = object : CustomListenerSpanProcessor.Listener {
                                override fun onEnd(span: ReadableSpan) {
                                    if (span.name == runTasksSpanName) {
                                        channel.close()
                                        return
                                    }
                                    channel.trySend(span)
                                }
                            }
                            try {
                                CustomListenerSpanProcessor.addListener(listener)
                                val task = call.receive<Task>()
                                async {
                                    runCatching {
                                        backend.runTask(task = TaskName.fromHierarchy(task.taskHierarchy))
                                    }.onFailure { e ->
                                        when (e) {
                                            is UserReadableError -> {
                                                logger.error("BUILD FAILED")
                                                channel.close(e)
                                                send(
                                                    ServerSentEvent(
                                                        e.message,
                                                        "error",
                                                        "error"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                channel.consumeAsFlow().collect {
                                    val serializedSpan = Json.encodeToString(it.toSerializable())
                                    send(ServerSentEvent(serializedSpan, "span", "span-data"))
                                }
                            } finally {
                                channel.close()
                                CustomListenerSpanProcessor.removeListener(listener)
                            }
                        }
                    }
                }
            }.start(wait = true)
        }
    }
}
