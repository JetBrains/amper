/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.processes.ProcessResult
import org.jetbrains.amper.telemetry.setListAttribute

val amperModuleKey: AttributeKey<String> = AttributeKey.stringKey("amper-module")

fun SpanBuilder.setAmperModule(module: AmperModule): SpanBuilder =
    setAttribute(amperModuleKey, module.userReadableName)

fun SpanBuilder.setFragments(fragments: List<Fragment>) =
    setListAttribute("fragments", fragments.map { it.name }.sorted())

/**
 * Sets attributes on this [Span] describing the given [result].
 */
fun Span.setProcessResultAttributes(result: ProcessResult) {
    setAttribute("exit-code", result.exitCode.toLong())
    setAttribute("stdout", result.stdout.filterAnsiCodes())
    setAttribute("stderr", result.stderr.filterAnsiCodes())
}

/**
 * Regex matching ANSI Control Sequence Introducer (CSI) codes.
 * The format is the following:
 *
 * * the `ESC [` introducer (`\u001B` escape character, followed by `[`)
 * * then any number (including none) of "parameter bytes" in the range 0x30–0x3F (ASCII `0–9:;<=>?`)
 * * then any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and `!"#$%&'()*+,-./`)
 * * then a single "final byte" in the range 0x40–0x7E (ASCII `@A–Z[\]^_``a–z{|}~`).
 *
 * See [ANSI escape codes](https://en.wikipedia.org/wiki/ANSI_escape_code).
 */
private val ansiCsiRegex = Regex("""\u001B\[[0–9:;<=>?]*[ !"#$%&'()*+,\-./]*[@A-Z\[\\\]^_`a-z{|}~]""")

private fun String.filterAnsiCodes(): String = replace(ansiCsiRegex, "")
