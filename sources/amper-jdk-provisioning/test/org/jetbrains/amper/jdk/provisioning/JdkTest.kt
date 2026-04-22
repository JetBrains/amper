/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import org.jetbrains.amper.test.TempDirExtension
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JdkTest {

    @RegisterExtension
    private val tempDirExtension = TempDirExtension()

    @Test
    fun majorVersion_jdk8Style() {
        val jdk = createJdkWithVersion("1.8.0_292")
        assertEquals(8, jdk.majorVersion)
    }

    @Test
    fun majorVersion_jdk9Style() {
        val jdk = createJdkWithVersion("9.0.1")
        assertEquals(9, jdk.majorVersion)
    }

    @Test
    fun majorVersion_jdk11() {
        val jdk = createJdkWithVersion("11.0.2")
        assertEquals(11, jdk.majorVersion)
    }

    @Test
    fun majorVersion_jdk21() {
        val jdk = createJdkWithVersion("21.0.1")
        assertEquals(21, jdk.majorVersion)
    }

    @Test
    fun majorVersion_simpleVersion() {
        val jdk = createJdkWithVersion("25")
        assertEquals(25, jdk.majorVersion)
    }

    @Test
    fun majorVersion_invalidVersion() {
        val jdk = createJdkWithVersion("invalid")
        assertFailsWith<IllegalStateException> {
            jdk.majorVersion
        }
    }

    private fun createJdkWithVersion(version: String): Jdk {
        val homeDir = tempDirExtension.path.resolve("jdk-$version")
        val binDir = homeDir.resolve("bin").createDirectories()
        binDir.resolve("java").createFile()
        return Jdk(
            homeDir = homeDir,
            version = version,
            distribution = null,
            source = "test",
        )
    }
}
