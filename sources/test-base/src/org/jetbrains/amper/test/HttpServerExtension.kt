/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.downloader.Downloader
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

/**
 * A JUnit Jupiter extension that provides an HTTP server to serve files from the given [wwwRoot] directory, and to
 * provide an HTTP cache for other types of URLs.
 *
 * * Use [wwwRootUrl] to access files from [wwwRoot].
 * * Use [cacheRootUrl] to use the actual network initially, and cache responses for the future.
 */
class HttpServerExtension(private val wwwRoot: Path) : Extension, BeforeEachCallback, AfterEachCallback {
    private val serverRef = AtomicReference<HttpServer>(null)
    private val requestedFilesRef = CopyOnWriteArrayList<Path>()

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
                httpExchange.sendResponseHeaders(400, -1)
                return@createContext
            }

            val filePath = wwwRoot.resolve(httpExchange.requestURI.path.removePrefix("/www/"))
            requestedFilesRef.add(filePath)

            if (!filePath.isRegularFile()) {
                httpExchange.sendResponseHeaders(404, -1)
                return@createContext
            }

            httpExchange.sendResponseHeaders(200, filePath.fileSize())
            filePath.inputStream().use { input -> input.copyTo(httpExchange.responseBody) }
        }
        server.createContext("/cache") { httpExchange ->
            if (httpExchange.requestMethod != "GET") {
                httpExchange.sendResponseHeaders(400, -1)
                return@createContext
            }

            val filePath = httpExchange.requestURI.path.removePrefix("/cache/")
            val url = "https://$filePath"

            val fakeUserCacheRoot = AmperUserCacheRoot(TestUtil.sharedTestCaches)
            println("Requested $url to $fakeUserCacheRoot")
            val cachedFile = runBlocking { Downloader.downloadFileToCacheLocation(url, fakeUserCacheRoot) }

            httpExchange.sendResponseHeaders(200, cachedFile.fileSize())
            cachedFile.inputStream().use { input -> input.copyTo(httpExchange.responseBody) }
        }
        server.start()
        serverRef.set(server)
    }

    override fun afterEach(context: ExtensionContext?) {
        val current = serverRef.getAndSet(null)!!
        current.stop(0)
    }
}
