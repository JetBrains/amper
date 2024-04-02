/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.test.assertEquals

class ErrorCollectorExtensionTest {
    @Test
    fun noExceptions() {
        val collector = ErrorCollectorExtension()
        collector.beforeEach(null)
        collector.afterEach(null)
    }

    @Test
    fun throwCollected() {
        val collector = ErrorCollectorExtension()
        collector.beforeEach(null)
        collector.addException(Exception("1"))
        collector.addException(Exception("2"))
        try {
            collector.afterEach(null)
            fail("must fail")
        } catch (t: Throwable) {
            assertEquals("1", t.message)
            assertEquals("2", t.suppressed.single().message)
        }
    }

    @Test
    fun beforeEachResetsCollected() {
        val collector = ErrorCollectorExtension()
        collector.beforeEach(null)
        collector.addException(Exception("1"))
        collector.beforeEach(null)
        collector.afterEach(null)
    }
}