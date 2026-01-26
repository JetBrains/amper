/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.*

class GeneratorTest {
    @TestFactory
    fun testFactory(): List<DynamicTest> {
        val randomNumbers = List(3) { it }
        return randomNumbers.map {
            DynamicTest.dynamicTest("Generated number is $it") {
                println("running generated test with $it")
            }
        }
    }
}
