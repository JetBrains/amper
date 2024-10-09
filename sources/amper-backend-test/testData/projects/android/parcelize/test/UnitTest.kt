/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.jetbrains.sample.app.Utils
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitTest {

    @Test
    fun test() {
        assertEquals(4, Utils.sum(2, 2))
    }
}