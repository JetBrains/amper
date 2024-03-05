// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.amper.diagnostics

import io.opentelemetry.sdk.trace.data.SpanData

interface AsyncSpanExporter {
  suspend fun export(spans: Collection<SpanData>)

  suspend fun flush() {}

  suspend fun shutdown() {}
}