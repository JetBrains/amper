/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.Ignore
import kotlin.test.Test

class IgnoredTest {
    @Ignore
    @Test
    fun ignoredWithoutMessage() {
    }

    @Test
    @Ignore("Ignored for a reason")
    fun ignoredWithMessage() {
    }
}

@Ignore("Ignoring the suite")
class IgnoredSuiteTest {
    @Test
    fun shouldntRun() {
    }
}