/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.example

import failgood.Test
import failgood.testsAbout
import kotlin.test.assertEquals

@Test
class SomeClassTest { // has to end with -Test

    val tests = testsAbout("my-test-group") {
        test("my-test-1") {
            assertEquals("ok", "ok")
        }
        test("my-test-2") {
            assertEquals("ok", "ok")
        }
    }
}