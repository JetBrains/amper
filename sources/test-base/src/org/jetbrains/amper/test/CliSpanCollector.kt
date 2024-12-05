/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.amper.test.spans.SpansTestCollector
import java.io.EOFException
import java.io.ObjectInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalContracts::class)
suspend fun collectSpansFromCli(
    block: suspend () -> Unit,
): SpansTestCollector {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    check(coroutineContext[SpanListenerPortContext] == null) {
        "Calls to collectSpansFromCli can't be nested."
    }
    return coroutineScope {
        val spans = mutableListOf<SpanData>()

        val serviceSocketChannel = ServerSocketChannel.open().apply {
            configureBlocking(false)
        }
        val serverSocket = serviceSocketChannel.socket().apply {
            // Let the OS pick the port, we'll provide it as a coroutine context element
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        }

        val listenJob = launch {
            while (isActive) {
                val socketChannel: SocketChannel? = serviceSocketChannel.accept()
                if (socketChannel == null) {
                    yield()
                    continue
                }

                // New connection, handle it
                launch {
                    try {
                        socketChannel.use {
                            // This blocks and is not cancellable
                            ObjectInputStream(it.socket().getInputStream().buffered()).use { objectStream ->
                                while (true) {
                                    spans += objectStream.readObject() as SpanData
                                }
                            }
                        }
                    } catch (_: EOFException) {
                        // Nothing
                    }
                }
            }
        }

        try {
            withContext(SpanListenerPortContext(port = serverSocket.localPort)) {
                block()
            }
        } finally {
            listenJob.cancelAndJoin()
            serviceSocketChannel.close()
        }

        object : SpansTestCollector {
            override val spans: List<SpanData> = spans
            override fun clearSpans() = throw UnsupportedOperationException()
        }
    }
}

class SpanListenerPortContext(
    val port: Int,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = SpanListenerPortContext

    companion object : CoroutineContext.Key<SpanListenerPortContext>
}