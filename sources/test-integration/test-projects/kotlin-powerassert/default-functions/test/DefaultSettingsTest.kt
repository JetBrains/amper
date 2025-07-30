/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.assert
import kotlin.test.assertEquals
import kotlin.test.Test

class DefaultSettingsTest {

    @Test
    fun testAssert() {
        val name1 = "george"
        val name2 = "fred"
        assert(name1 == name2 + name1[2])
    }

    @Test
    fun testAssertEquals() {
        val name1 = "george"
        val name2 = "fred"
        assertEquals(name1, name2.substring(2, name2.length))
    }
}
