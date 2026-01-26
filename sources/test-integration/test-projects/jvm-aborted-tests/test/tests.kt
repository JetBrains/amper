/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue

class AbortedTest {
    @Test
    fun assumeWithoutMessage() {
        println("running assume without message")
        assumeTrue(1 == 2)
    }

    @Test
    fun assumeWithMessage() {
        println("running assume with message")
        assumeTrue(1 == 2, "1 is not equal to 2 in this universe")
    }
}