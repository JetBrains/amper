/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.test.assertEqualsWithDiff

internal fun assertAlphabeticalOrder(items: List<String>, moniker: String) {
    assertEqualsWithDiff(
        expected = items.sorted(),
        actual = items,
        message = "$moniker are not in alphabetical order. Tip: you can select the lines and use the 'Sort lines' IDEA action",
    )
}
