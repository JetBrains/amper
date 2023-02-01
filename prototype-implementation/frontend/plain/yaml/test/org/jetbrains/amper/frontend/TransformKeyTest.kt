/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class TransformKeyTest {
    @Test
    fun test1() {
        assertEquals("dependencies@test", "test-dependencies".transformKey())
    }

    @Test
    fun test2() {
        assertEquals("dependencies@ios+test", "test-dependencies@ios".transformKey())
    }

    @Test
    fun test3() {
        assertEquals("dependencies@ios+release+test", "test-dependencies@ios+release".transformKey())
    }

    @Test
    fun test4() {
        assertEquals("something", "something".transformKey())
    }
}