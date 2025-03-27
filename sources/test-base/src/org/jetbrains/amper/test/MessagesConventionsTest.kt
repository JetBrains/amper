/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.text.get

abstract class MessagesConventionsTest(private val bundleName: String) {
    @Test
    fun messagesAreSortedAlphabetically() {
        val bundleText = javaClass.getResource("/messages/$bundleName.properties")?.readText()
            ?: error("$bundleName.properties not found")

        // we don't parse this as Properties object because we would lose the order
        val keys = bundleText
            .replace("\\\r\n", "")
            .replace("\\\r", "")
            .replace("\\\n", "")
            .lines()
            .filter { it.isNotEmpty() }
            .map { parsePropertyKey(it) }

        assertEquals(keys.sorted(), keys, "$bundleName.properties should be sorted alphabetically (to avoid git conflicts, and for easier navigability of the file)")
    }
}

private val singleLinePropertyRegex = Regex("(?<key>[^=]+)=(?<value>.*)")

private fun parsePropertyKey(line: String): String {
    val match = singleLinePropertyRegex.matchEntire(line)
        ?: error("Line doesn't match the expected key=value syntax: $line")
    return match.groups["key"]?.value ?: error("Key group is missing from match (impossible!)")
}
