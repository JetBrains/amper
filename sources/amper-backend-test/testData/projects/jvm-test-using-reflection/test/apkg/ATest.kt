/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package apkg

import kotlin.test.Test

class ATest {
    @Test
    fun smoke() {
        ATest::class.java.constructors.single()
    }
}
