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
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.logging.ServerWriter
import org.jetbrains.amper.cli.logging.sessionIdKey
import org.jetbrains.amper.cli.logging.useKey
import org.jetbrains.amper.cli.logging.useServerValue
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.frontend.TaskName
import org.slf4j.MDC
import org.tinylog.Level
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Task(val taskHierarchy: List<String>)

private const val defaultPort = 8000

internal class ServerCommand : AmperSubcommand(name = "server") {

    override val hiddenFromHelp: Boolean = true

    private val port by option("-p", "--port", help = "The port to listen on")
        .int(acceptsValueWithoutName = true)
        .default(defaultPort)

    private val logLevel by option("-l", "--log-level", help = "The task log level")
        .choiceWithTypoSuggestion(
            mapOf(
                "debug" to Level.DEBUG,
                "info" to Level.INFO,
                "warn" to Level.WARN,
                "error" to Level.ERROR,
                "off" to Level.OFF,
            ), ignoreCase = true
        ).default(Level.INFO)

    override fun help(context: Context): String =
        "Start a server that accepts tasks from Amper and runs them. The server runs on port $defaultPort by default."

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun run() {
        withBackend(commonOptions, commandName, terminal) { backend ->
            embeddedServer(Netty, port = port) {
                install(ContentNegotiation) { json() }
                install(SSE)
                routing {
                    route("/task", HttpMethod.Post) {
                        sse {
                            val requestId = Uuid.random().toHexString()
                            MDC.put(useKey, useServerValue)
                            MDC.put(sessionIdKey, requestId)
                            val task = call.receive<Task>()

                            launch {
                                ServerWriter.logFlow
                                    .filter { it.uuid == requestId }
                                    .map { it.entry }
                                    .filter { it.level.ordinal >= logLevel.ordinal }
                                    .collect {
                                        send(
                                            ServerSentEvent(
                                                it.message,
                                                it.level.name.lowercase(),
                                                "log"
                                            )
                                        )
                                    }
                            }

                            runCatching {
                                send(
                                    ServerSentEvent(
                                        "Build started",
                                        "event",
                                        "event"
                                    )
                                )

                                withContext(MDCContext()) {
                                    backend.runTask(task = TaskName.fromHierarchy(task.taskHierarchy))
                                }
                            }.onFailure { e ->
                                when (e) {
                                    is UserReadableError -> {
                                        logger.error("BUILD FAILED")
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

                            send(
                                ServerSentEvent(
                                    "Build finished",
                                    "event",
                                    "event"
                                )
                            )

                            close()
                        }
                    }
                }
            }.start(wait = true)
        }
    }
}
