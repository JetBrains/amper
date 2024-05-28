/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.assertions

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.backend.test.extensions.OpenTelemetryCollectorExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class FilteredSpans(
    private val matchingSpans: List<SpanData>,
    private val filters: List<String>,
) {
    private val filtersDescription: String = filters.joinToString()

    fun filter(description: String, predicate: (SpanData) -> Boolean): FilteredSpans = copy(
        matchingSpans = matchingSpans.filter(predicate),
        filters = filters + description,
    )

    fun assertSingle(): SpanData {
        assertFalse(matchingSpans.isEmpty(), "No span matching the filters: $filtersDescription")
        assertFalse(matchingSpans.size > 1, "More than 1 span matching the filters: $filtersDescription")
        return matchingSpans.single()
    }

    fun assertTimes(times: Int): List<SpanData> {
        assertFalse(matchingSpans.isEmpty(), "No span matching the filters: $filtersDescription")
        assertEquals(times, matchingSpans.size, "Not exactly $times spans matching the filters: $filtersDescription")
        return matchingSpans
    }

    fun assertNone() {
        assertTrue(
            matchingSpans.isEmpty(),
            "Got ${matchingSpans.size} but expected no spans matching the filters: $filtersDescription"
        )
    }

    fun all() = matchingSpans
}

fun OpenTelemetryCollectorExtension.spansNamed(name: String): FilteredSpans = filteredSpans.withName(name)

private val OpenTelemetryCollectorExtension.filteredSpans: FilteredSpans
    get() = FilteredSpans(spans, emptyList())

fun FilteredSpans.withName(name: String): FilteredSpans = filter("name='$name'") { it.name == name }

fun <T> FilteredSpans.withAttribute(key: AttributeKey<T>, value: T): FilteredSpans =
    filter("attr['${key.key}']='$value'") { it.attributes[key] == value }
