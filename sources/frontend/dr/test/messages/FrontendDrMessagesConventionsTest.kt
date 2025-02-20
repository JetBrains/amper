/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package messages

import kotlin.test.Test
import kotlin.test.assertEquals

class FrontendDrMessagesConventionsTest {

    @Test
    fun messagesAreSortedAlphabetically() {
        val bundleText = javaClass.getResource("/messages/FrontendDr.properties")?.readText()
            ?: error("FrontendDr.properties not found")

        // we don't parse this as Properties object because we would lose the order
        val keys = bundleText
            .replace("\\\r\n", "")
            .replace("\\\r", "")
            .replace("\\\n", "")
            .lines()
            .filter { it.isNotEmpty() }
            .map { parsePropertyKey(it) }

        assertEquals(keys.sorted(), keys, "FrontendDr.properties should be sorted alphabetically (to avoid git conflicts, and for easier navigability of the file)")
    }
}

private val singleLinePropertyRegex = Regex("(?<key>[^=]+)=(?<value>.*)")

private fun parsePropertyKey(line: String): String {
    val match = singleLinePropertyRegex.matchEntire(line)
        ?: error("Line doesn't match the expected key=value syntax: $line")
    return match.groups["key"]?.value ?: error("Key group is missing from match (impossible!)")
}