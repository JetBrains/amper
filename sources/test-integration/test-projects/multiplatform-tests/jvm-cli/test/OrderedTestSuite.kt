/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package com.example.jvmcli

import kotlin.test.*
import org.junit.runner.*
import org.junit.runners.*

// Explicit order for the stability of Amper tests, because JUnit 4 doesn't guarantee the order of test classes and
// we may get variable results on Linux.
@RunWith(Suite::class)
@Suite.SuiteClasses(
    JvmIntegrationTest::class,
    MyClass1Test::class,
    MyClass2Test::class
)
class OrderedTestSuite
