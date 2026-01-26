/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdks

import kotlin.test.Test

class FetchIjJdksTest {

    @Test
    fun canFetchJdks() {
        val jdks = fetchIjJdks()
        assert(jdks.isNotEmpty())
    }
}
