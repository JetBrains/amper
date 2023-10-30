/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageBundleTest {
    private val bundle = MessageBundle("messages.CoreTestBundle")

    @Test
    fun `message without arguments`() {
        assertEquals("Test message", bundle.message("test.message"))
    }

    @Test
    fun `message with arguments`() {
        assertEquals("Test message with arg1 and arg2", bundle.message("test.message.arguments", "arg1", "arg2"))
    }

    @Test
    fun `message with missing key`() {
        assertEquals("test.missing", bundle.message("test.missing"))
    }
}
