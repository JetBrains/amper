/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */


package com.example.jvmcli

import kotlin.test.Test
import kotlin.test.assertTrue

class JvmIntegrationTest {

    @Test
    fun integrationTest() {
        println("output line 1 in JvmIntegrationTest.integrationTest")
        System.err.println("error line 1 in JvmIntegrationTest.integrationTest")
        println("output line 2 in JvmIntegrationTest.integrationTest")
        System.err.println("error line 2 in JvmIntegrationTest.integrationTest")
    }
}
