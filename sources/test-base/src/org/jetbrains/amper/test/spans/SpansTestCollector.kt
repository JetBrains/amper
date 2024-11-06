/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.spans

import io.opentelemetry.sdk.trace.data.SpanData

interface SpansTestCollector {
    /**
     * All collected spans so far
     */
    val spans: List<SpanData>

    /**
     * Clears all the collected span data
     */
    fun clearSpans()
}