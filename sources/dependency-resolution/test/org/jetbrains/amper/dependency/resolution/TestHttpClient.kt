/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.fail
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class TestHttpClient(
    val client: HttpClient,
    private val errorProducingUrls: List<String>,
    private val failOnErrorUrl: Boolean = true
) : HttpClient() {

    val processedUrls: MutableList<URI> = mutableListOf()

    override fun cookieHandler(): Optional<CookieHandler?>? = client.cookieHandler()
    override fun connectTimeout(): Optional<Duration?>? = client.connectTimeout()
    override fun followRedirects(): Redirect? = client.followRedirects()
    override fun proxy(): Optional<ProxySelector?>? = client.proxy()
    override fun sslContext(): SSLContext? = client.sslContext()
    override fun sslParameters(): SSLParameters? = client.sslParameters()
    override fun authenticator(): Optional<Authenticator?>? = client.authenticator()
    override fun version(): Version? = client.version()
    override fun executor(): Optional<Executor?>? = client.executor()
    override fun <T : Any?> send(p0: HttpRequest?, p1: HttpResponse.BodyHandler<T?>?) =
        withUrlsCheck(p0) { client.send(p0, p1) }

    override fun <T : Any?> sendAsync(p0: HttpRequest?, p1: HttpResponse.BodyHandler<T?>?) =
        withUrlsCheck(p0) { client.sendAsync(p0, p1) }

    override fun <T : Any?> sendAsync(
        p0: HttpRequest?,
        p1: HttpResponse.BodyHandler<T?>?,
        p2: HttpResponse.PushPromiseHandler<T?>?
    ) = withUrlsCheck(p0) { client.sendAsync(p0, p1, p2) }

    private fun <T> withUrlsCheck(request: HttpRequest?, block: () -> T): T {
        request?.let {
            if (errorProducingUrls.any { request.uri() == URI.create(it) }) {
                val message = "Request to one of error-producing URLs: ${request.uri()}"
                if (failOnErrorUrl) fail(message) else  error(message)
            }

            processedUrls.add(request.uri())
        }

        return block()
    }

    companion object {
        /**
         * @param urlThatShouldNotBeDownloaded any attempt of HttpClient to communicate with one of the URLs
         * ends up in either error being thrown or immediate test failure depending on the parameter [failOnErrorUrl]
         *
         * @param failOnErrorUrl set to true if the test should immediately fail
         * on an attempt to reach one of the given URLs [urlThatShouldNotBeDownloaded],
         * otherwise this HttpClient throws an error only that is processed by resolution as any other error.
         */
        fun create(urlThatShouldNotBeDownloaded: List<String>, failOnErrorUrl: Boolean = true): TestHttpClient {
            val client = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NORMAL)
                .sslContext(SSLContext.getDefault())
                .connectTimeout(Duration.ofSeconds(20))
                .build()
            return TestHttpClient(client, urlThatShouldNotBeDownloaded, failOnErrorUrl)
        }
    }
}

