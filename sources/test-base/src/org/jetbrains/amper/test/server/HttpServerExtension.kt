/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.fileSize

sealed interface WwwResult {
    /** Respond with the local file corresponding to the URL path (the default). */
    data object LocalFile : WwwResult

    /** Respond with an HTTP redirect to the given [newUrl] */
    data class Redirect(val newUrl: String) : WwwResult

    /** Respond with the contents of the file at the given [url]. The download is cached. */
    data class Download(val url: String) : WwwResult
}

/**
 * A JUnit Jupiter extension that provides an HTTP server to serve files from the given [wwwRoot] directory, and to
 * provide an HTTP cache for other types of URLs.
 *
 * * Use [wwwRootUrl] to access files from [wwwRoot].
 * * Use [cacheRootUrl] to use the actual network initially, and cache responses for the future.
 */
class HttpServerExtension(
    private val wwwRoot: Path,
    private val wwwInterceptor: (String) -> WwwResult = { WwwResult.LocalFile },
) : Extension, BeforeEachCallback, AfterEachCallback {
    private val serverRef = AtomicReference<HttpServer>(null)
    private val requestedFilesRef = CopyOnWriteArrayList<Path>()
    private val logger = LoggerFactory.getLogger(javaClass)

    private val port: Int
        get() = serverRef.get()?.address?.port ?: error("Port is null")

    /**
     * The URL to expose the files from [wwwRoot].
     * Calling `HTTP GET <wwwRootUrl>/some/file.zip` returns the contents of the file located at
     * `<wwwRoot>/some/file.zip`.
     */
    val wwwRootUrl: String
        get() = "http://127.0.0.1:$port/www"

    /**
     * The URL to access the internet via this proxy that adds caching.
     * Calling `HTTP GET <cacheRootUrl>/some/url/path` returns the contents of the file located at
     * `https://some/url/path`; first from the internet, then from the local cache.
     */
    val cacheRootUrl: String
        get() = "http://127.0.0.1:$port/cache"

    /**
     * Contains the list of files from [wwwRoot] that were requested so far in the current test.
     */
    val requestedFiles: List<Path>
        get() = requestedFilesRef.toList()

    override fun beforeEach(context: ExtensionContext?) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 5)
        server.createContext("/www") { httpExchange ->
            if (httpExchange.requestMethod != "GET") {
                httpExchange.respondInvalidMethod()
                return@createContext
            }

            val effectiveUrlPath = httpExchange.requestURI.path.removePrefix("/www/")
            when (val result = wwwInterceptor(effectiveUrlPath)) {
                is WwwResult.Redirect -> httpExchange.respondRedirect(result.newUrl)
                is WwwResult.Download -> httpExchange.respondWithDownloadedFile(result.url)
                WwwResult.LocalFile -> {
                    val filePath = wwwRoot.resolve(effectiveUrlPath)
                    requestedFilesRef.add(filePath)
                    httpExchange.respondWithLocalFile(filePath)
                }
            }
        }
        server.createContext("/cache") { httpExchange ->
            if (httpExchange.requestMethod != "GET") {
                httpExchange.respondInvalidMethod()
                return@createContext
            }

            val filePath = httpExchange.requestURI.path.removePrefix("/cache/")
            httpExchange.respondWithDownloadedFile(url = "https://$filePath")
        }
        server.start()
        serverRef.set(server)
    }

    private fun HttpExchange.respondInvalidMethod() {
        logger.error("HTTP ${this.requestMethod} method is not supported in test server, expected GET")
        respondInvalidMethod(listOf("GET"))
    }

    private fun HttpExchange.respondWithDownloadedFile(url: String) {
        try {
            runBlocking(Dispatchers.IO) {
                val cachedFile = Downloader.downloadFileToCacheLocation(url, AmperUserCacheRoot(Dirs.persistentHttpCache))
                sendResponseHeaders(200, cachedFile.fileSize())
                responseBody.writeFileContents(cachedFile)
            }
        } catch (e: Throwable) {
            // Throwable ^^ is not used lightly here, it catches ExceptionInInitializerError for uninitialized Ktor engine
            // Those exceptions don't seem to propagate anywhere otherwise.
            logger.error("Exception when downloading from $url", e)
            respondInternalServerError("The test proxy server failed to download from $url:\n$e", cause = e)
        }
    }

    override fun afterEach(context: ExtensionContext?) {
        val current = serverRef.getAndSet(null)!!
        current.stop(0)
    }
}
