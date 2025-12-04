/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.junit.jupiter.api.TestInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class HasherTest: BaseDRTest() {

    /**
     * This test checks that all expected hashers are successfully created
     * (all digests are available in JVM)
     */
    @Test
    fun testCreateHashers(testInfo: TestInfo) = runDrTest {
        println("Java version: ${ Runtime.version() }")

        assertEquals(
            setOf("sha512", "sha256", "sha1", "md5"),
            createHashers().map { it.algorithm.name }.toSet()
        )
    }
}