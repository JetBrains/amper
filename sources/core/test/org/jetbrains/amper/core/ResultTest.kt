/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import org.jetbrains.amper.core.*
import org.jetbrains.amper.core.Result.Companion.failure
import org.jetbrains.amper.core.Result.Companion.success
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ResultTest {
    @Test
    fun `get() method`() {
        assertEquals(42, success(42).get())
        assertThrows<RuntimeException> { failure<Int>(RuntimeException()).get() }
    }

    @Test
    fun `getOrNull() method`() {
        assertEquals(42, success(42).getOrNull())
        assertEquals(null, failure<Int>(RuntimeException()).getOrNull())
    }

    @Test
    fun `getOrElse() method`() {
        assertEquals(42, success(42).getOrElse { 0 })
        assertEquals(0, failure<Int>(RuntimeException()).getOrElse { 0 })
    }

    @Test
    fun `map() method`() {
        assertEquals("42", success(42).map(Int::toString).get())
        assertThrows<RuntimeException> { failure<Int>(RuntimeException()).map(Int::toString).get() }
    }

    @Test
    fun `flatMap() method`() {
        assertEquals("42", success(42).flatMap { success(it.toString()) }.get())
        assertThrows<RuntimeException> { failure<Int>(RuntimeException()).flatMap { success(it.toString()) }.get() }
    }

    @Test
    fun `unwrap() method`() {
        assertEquals(listOf(1, 2, 3), listOf(success(1), success(2), success(3)).unwrap())
        assertThrows<RuntimeException> { listOf(success(1), failure(RuntimeException()), success(3)).unwrap() }
    }
}
