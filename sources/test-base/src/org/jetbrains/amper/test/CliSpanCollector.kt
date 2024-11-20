/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.amper.diagnostics.rmi.LoopbackClientSocketFactory
import org.jetbrains.amper.diagnostics.rmi.LoopbackServerSocketFactory
import org.jetbrains.amper.diagnostics.rmi.SpanExporterService
import org.jetbrains.amper.test.spans.SpansTestCollector
import java.rmi.NoSuchObjectException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry
import java.rmi.server.UnicastRemoteObject
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

class CliSpanCollector : SpansTestCollector {
    // Do not inline - have to keep a strong reference
    private lateinit var serviceRef: SpanExporterService

    private val collectedSpans = mutableListOf<SpanData>()
    private fun addSpan(spanData: SpanData) = synchronized(collectedSpans) { collectedSpans.add(spanData) }

    override val spans: List<SpanData>
        get() = synchronized(collectedSpans) { collectedSpans.toList() }
    override fun clearSpans() = synchronized(collectedSpans) { collectedSpans.clear() }

    companion object {
        private val registry: Registry by lazy {
            try {
                LocateRegistry.getRegistry(LoopbackClientSocketFactory.hostName,
                    SpanExporterService.PORT, LoopbackClientSocketFactory,
                ).also {
                    // We call list() here to trigger registry location, otherwise `getRegistry` doesn't throw even if
                    // it doesn't exist yet.
                    it.list()
                }
            } catch (e: NoSuchObjectException) {
                LocateRegistry.createRegistry(
                    SpanExporterService.PORT, LoopbackClientSocketFactory, LoopbackServerSocketFactory,
                )
            }
        }

        fun runCliTestWithCollector(
            timeout: Duration = Duration.INFINITE,
            block: suspend CliSpanCollector.() -> Unit,
        ) {
            val serviceName = UUID.randomUUID().toString()

            runTest(
                timeout = timeout,
            ) {
                val testCollector = CliSpanCollector()

                val serviceImpl = object : SpanExporterService {
                    override fun export(spanData: List<SpanData>) {
                        for (spanDatum in spanData) {
                            testCollector.addSpan(spanDatum)
                        }
                    }
                }
                testCollector.serviceRef =
                    UnicastRemoteObject.exportObject(serviceImpl, SpanExporterService.PORT,
                        LoopbackClientSocketFactory, LoopbackServerSocketFactory) as SpanExporterService

                registry.bind(serviceName, testCollector.serviceRef)
                try {
                    withContext(SpanExporterServiceNameContext(serviceName)) {
                        testCollector.block()
                    }
                } finally {
                    UnicastRemoteObject.unexportObject(serviceImpl, true)
                    registry.unbind(serviceName)
                }
            }
        }
    }

    class SpanExporterServiceNameContext(
        val serviceName: String,
    ) : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = SpanExporterServiceNameContext

        companion object : CoroutineContext.Key<SpanExporterServiceNameContext>
    }
}