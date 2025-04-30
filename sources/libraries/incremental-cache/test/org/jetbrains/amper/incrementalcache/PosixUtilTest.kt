/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals

class PosixUtilTest {
    @Test
    fun toUnixMode1() {
        val perms = PosixFilePermissions.fromString("rwxr-xr--")
        val unixMode = PosixUtil.toUnixMode(perms)
        assertEquals(Integer.valueOf("754", 8), unixMode)
    }

    @Test
    fun toUnixMode2() {
        val perms = PosixFilePermissions.fromString("rwxrwxrwx")
        val unixMode = PosixUtil.toUnixMode(perms)
        assertEquals(Integer.valueOf("777", 8), unixMode)
    }
}