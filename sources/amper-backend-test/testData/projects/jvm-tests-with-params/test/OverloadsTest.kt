/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package com.example.testswithparams

import kotlin.test.*
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestReporter

class OverloadsTest {

    // test names are intentionally the same

    @Test
    fun test() {
        println("running OverloadsTest.test()")
    }

    @Test
    fun test(testInfo: TestInfo) {
        println("running OverloadsTest.test(TestInfo)")
    }

    @Test
    fun test(testInfo: TestInfo, testReporter: TestReporter) {
        println("running OverloadsTest.test(TestInfo, TestReporter)")
    }
}
