/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.sdk.trace.data.SpanData
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.processes.ProcessResult

val amperModuleKey: AttributeKey<String> = AttributeKey.stringKey("amper-module")

fun SpanBuilder.setAmperModule(module: AmperModule): SpanBuilder =
    setAttribute(amperModuleKey, module.userReadableName)

fun SpanBuilder.setFragments(fragments: List<Fragment>) =
    setListAttribute("fragments", fragments.map { it.name }.sorted())

fun SpanBuilder.setListAttribute(key: String, list: List<String>): SpanBuilder =
    setAttribute(AttributeKey.stringArrayKey(key), list)

fun Span.setListAttribute(key: String, list: List<String>): Span =
    setAttribute(AttributeKey.stringArrayKey(key), list)

fun SpanBuilder.setMapAttribute(key: String, map: Map<String, String>): SpanBuilder =
    setListAttribute(key, map.map { "${it.key}=${it.value}" }.sorted())

fun Span.setMapAttribute(key: String, map: Map<String, String>): Span =
    setListAttribute(key, map.map { "${it.key}=${it.value}" }.sorted())

/**
 * Sets attributes on this [Span] describing the given [result].
 */
fun Span.setProcessResultAttributes(result: ProcessResult) {
    setAttribute("exit-code", result.exitCode.toLong())
    setAttribute("stdout", result.stdout)
    setAttribute("stderr", result.stderr)
}

/**
 * Returns the value of the attribute [key], or throws [NoSuchElementException] if no such attribute exists in this span.
 */
fun <T> SpanData.getAttribute(key: AttributeKey<T>) = attributes[key]
    ?: throw NoSuchElementException("attribute '$key' not found in span '$name'")
