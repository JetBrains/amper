/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.composehotreload.recompiler

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runProcess
import org.jetbrains.compose.devtools.api.Recompiler
import org.jetbrains.compose.devtools.api.RecompilerContext
import org.jetbrains.compose.devtools.api.RecompilerExtension
import org.jetbrains.compose.reload.core.ExitCode
import org.jetbrains.compose.reload.core.HotReloadEnvironment
import org.jetbrains.compose.reload.core.createLogger
import org.jetbrains.compose.reload.core.error
import org.jetbrains.compose.reload.core.info
import org.jetbrains.compose.reload.orchestration.OrchestrationMessage
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

private val logger = createLogger()

class AmperRecompilerExtension : RecompilerExtension {
    override fun createRecompiler(): Recompiler = AmperRecompiler()
}

class AmperRecompiler() : Recompiler, AutoCloseable {

    @Serializable
    data class ReloadRequest(val taskHierarchy: List<String>)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = HttpClient(CIO) {
        install(SSE)
        install(ContentNegotiation) {
            json()
        }
    }

    private val amperServerPort =
        System.getenv()[ENV_AMPER_SERVER_PORT] ?: error("AMPER_SERVER_PORT env variable is not set")
    private val amperBuildTask =
        System.getenv()[ENV_AMPER_BUILD_TASK] ?: error("AMPER_BUILD_TASK env variable is not set")
    private val amperBuildRoot = System.getenv()[ENV_AMPER_BUILD_ROOT]
        ?.let { Path(it) } ?: error("AMPER_BUILD_ROOT env variable is not set")

    override val name: String = "Amper Recompiler"

    init {
        start()
        Runtime.getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    override suspend fun buildAndReload(context: RecompilerContext): ExitCode {
        context.orchestration.send(OrchestrationMessage.BuildStarted())
        val parts = amperBuildTask.removePrefix(":").split(":")
        if (parts.size < 2) {
            logger.error("Invalid amperBuildTask format. Expected ':module:task' or ':module:submodule:task', got $amperBuildTask")
            return ExitCode(1)
        }

        httpClient.sse({
            header(HttpHeaders.ContentType, ContentType.Application.Json)

            url {
                host = "localhost"
                port = amperServerPort.toInt()
                method = Post
                path("task")
            }

            setBody(ReloadRequest(parts))
        }) {
            incoming.collect { event ->
                when (event.id) {
                    "error" -> {
                        context.logger.error(event.data ?: "Unknown error")
                        context.logger.error("BUILD FAILED")
                    }
                    else -> {
                        context.logger.info(event.data ?: "Unknown event")
                    }
                }
            }
        }

        return ExitCode(0)
    }

    private fun start() {
        val orchestrationPort = HotReloadEnvironment.orchestrationPort ?: error("Missing orchestration port")
        val completableDeferred = CompletableDeferred<Unit>()
        val streamingOutputListener = object : ProcessOutputListener {
            override fun onStdoutLine(line: String, pid: Long) {
                if (line.contains("Responding at")) {
                    completableDeferred.complete(Unit)
                }
            }

            override fun onStderrLine(line: String, pid: Long) {
                completableDeferred.completeExceptionally(Throwable("Amper server error: $line"))
            }
        }
        coroutineScope.launch {
            val exitCode = runProcess(
                workingDir = amperBuildRoot,
                command = listOf("./amper", "server", "--port", amperServerPort),
                environment = mapOf(ENV_COMPOSE_HOT_RELOAD_ORCHESTRATION_PORT to orchestrationPort.toString()),
                outputListener = streamingOutputListener
            )
            if (exitCode != 0) {
                logger.error("Amper server exited with code $exitCode")
                completableDeferred.completeExceptionally(Throwable("Amper server exited with code $exitCode"))
            }
        }
        runBlocking {
            val timeMillis = TimeUnit.SECONDS.toMillis(10)
            withTimeout(timeMillis) {
                completableDeferred.await()
            }
        }
    }

    override fun close() {
        coroutineScope.cancel()
    }
}
