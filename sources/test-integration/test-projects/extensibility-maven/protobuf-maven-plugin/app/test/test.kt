/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package apkg

import apkg.*
import kotlin.test.Test

class ATest {
    @Test
    fun test() {
        val bar = bar {
            foo = foo {
                value = 47
            }
        }

        println("Hello from the proto test! Request value is ${bar.foo.value}")
    }
}