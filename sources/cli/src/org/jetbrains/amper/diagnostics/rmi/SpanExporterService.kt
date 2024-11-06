/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics.rmi

import io.opentelemetry.sdk.trace.data.SpanData
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.Registry

interface SpanExporterService : Remote {
    @Throws(RemoteException::class)
    fun export(spanData: List<SpanData>)

    companion object {
        const val PORT = Registry.REGISTRY_PORT
        const val NAME_ENV_VAR = "AMPER_RMI_SPAN_EXPORTER_SERVICE_NAME"
    }
}