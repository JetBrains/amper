/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.options

import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import org.jetbrains.amper.cli.userReadableError

internal const val UserJvmArgsOption = "--jvm-args"

internal fun ParameterHolder.userJvmArgsOption(help: String) = option(UserJvmArgsOption, help = help)
    .transformAll { values ->
        values.flatMap { it.splitArgsHonoringQuotes() }
    }

internal fun String.splitArgsHonoringQuotes(): List<String> {
    val args = mutableListOf<String>()
    val currentArg = StringBuilder()
    var inQuotes = false
    var lastOpenQuoteIndex = -1
    var escaping = false
    var hasPendingArg = false

    for ((i, c) in withIndex()) {
        if (escaping) {
            currentArg.append(c)
            hasPendingArg = true
            escaping = false
            continue
        }
        when {
            c == '\\' -> {
                escaping = true
            }
            c == '"' -> {
                lastOpenQuoteIndex = if (inQuotes) -1 else i
                inQuotes = !inQuotes
                hasPendingArg = true
            }
            c == ' ' && !inQuotes -> {
                if (hasPendingArg) { // multiple spaces shouldn't yield empty args
                    args.add(currentArg.toString())
                    currentArg.clear()
                    hasPendingArg = false
                }
            }
            else -> {
                currentArg.append(c)
                hasPendingArg = true
            }
        }
    }
    if (escaping) {
        userReadableError("Dangling escape character '\\' at the end of the $UserJvmArgsOption value:\n$this")
    }
    if (inQuotes) {
        val arrowLine = " ".repeat(lastOpenQuoteIndex) + "^"
        userReadableError("Unclosed quote at index $lastOpenQuoteIndex in $UserJvmArgsOption:\n$this\n$arrowLine")
    }
    if (hasPendingArg) {
        args.add(currentArg.toString())
    }
    return args
}
