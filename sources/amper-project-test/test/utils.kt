import kotlin.test.assertEquals

/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

internal fun assertAlphabeticalOrder(items: List<String>, moniker: String) {
    val expected = items.sorted().joinToString("\n")
    val actual = items.joinToString("\n")
    assertEquals(expected, actual, "$moniker are not in alphabetical order. Tip: you can select the lines and use the 'Sort lines' IDEA action.")
}
