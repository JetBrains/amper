/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.server

import com.sun.net.httpserver.Authenticator
import com.sun.net.httpserver.HttpServer
import org.jetbrains.amper.test.processes.err
import org.junit.jupiter.api.TestReporter
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

data class RequestHistory(
    val requests: List<Request>
)

data class Request(
    val method: String,
    val path: String,
) {
    override fun toString(): String = "$method $path"
}

suspend fun withFileServer(
    wwwRoot: Path,
    testReporter: TestReporter,
    authenticator: Authenticator? = null,
    block: suspend (baseUrl: String) -> Unit,
) : RequestHistory {
    val requests = mutableListOf<Request>()
    val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10)
    try {
        val context = httpServer.createContext("/") { exchange ->
            requests.add(Request(exchange.requestMethod, exchange.requestURI.path))

            val fsPath = wwwRoot.resolve(exchange.requestURI.path.trim('/')).normalize()
            if (!fsPath.startsWith(wwwRoot)) {
                exchange.respondInvalidRequest("path cannot point outside the root, got '$fsPath'")
                return@createContext
            }

            when (exchange.requestMethod) {
                "HEAD" -> exchange.respondWithLocalFile(fsPath, headersOnly = true)
                "GET" -> exchange.respondWithLocalFile(fsPath)
                "PUT" -> {
                    val bytes = exchange.requestBody.use { it.readBytes() }
                    val contentLength = exchange.requestHeaders.getFirst("Content-Length")?.toInt()
                    if (contentLength != null && contentLength != bytes.size) {
                        exchange.respondInvalidRequest("Content-length header ($contentLength) doesn't match the body size (${bytes.size})")
                        return@createContext
                    }
                    try {
                        val fileExisted = fsPath.exists()
                        fsPath.parent.createDirectories()
                        fsPath.writeBytes(bytes)
                        exchange.sendResponseHeaders(if (fileExisted) 200 else 201, -1)
                        exchange.responseBody.close()
                    } catch (e: Exception) {
                        val testStderr = PrintStream(testReporter.err("[test server]"))
                        testStderr.println("Failed to write file '$fsPath': $e")
                        e.printStackTrace(testStderr)
                        exchange.respondInternalServerError(cause = e)
                        return@createContext
                    }
                }
                else -> exchange.respondInvalidMethod(listOf("GET", "PUT", "HEAD"))
            }
        }
        context.authenticator = authenticator

        httpServer.start()

        block("http://127.0.0.1:${httpServer.address.port}")
    } finally {
        httpServer.stop(15)
    }
    return RequestHistory(requests)
}
