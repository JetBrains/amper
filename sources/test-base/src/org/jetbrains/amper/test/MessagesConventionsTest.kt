/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.text.get

abstract class MessagesConventionsTest(private val bundleName: String) {
    @Test
    fun messagesAreSortedAlphabetically() {
        val bundlePath = Path("resources/messages/$bundleName.properties").takeIf { it.exists() }
        val bundleText = bundlePath?.readText()?.trim() ?: error("$bundleName.properties file not found")

        // We don't parse this as a Properties object because we will lose the order.
        val keyValueLines = buildList<StringBuilder> {
            bundleText.lines().forEach {
                if (lastOrNull()?.endsWith('\\') != true) add(StringBuilder().append(it))
                else last().append("\n$it")
            }
        }

        val sorted = keyValueLines
            .map { it.toString() }
            .filter { it.isNotEmpty() }
            .sortedBy { parsePropertyKey(it) }
            .joinToString("\n")

        assertEqualsIgnoreLineSeparator(bundleText, sorted, bundlePath)
    }
}

private val singleLinePropertyRegex = Regex("(?<key>[^=]+)=(?<value>.*)", RegexOption.DOT_MATCHES_ALL)

private fun parsePropertyKey(line: String): String {
    val match = singleLinePropertyRegex.matchEntire(line)
        ?: error("Line doesn't match the expected key=value syntax: $line")
    return match.groups["key"]?.value ?: error("Key group is missing from match (impossible!)")
}
