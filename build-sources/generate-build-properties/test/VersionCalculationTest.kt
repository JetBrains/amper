/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionCalculationTest {
    @Test
    fun `majorAndMinorVersion for release`() {
        assertEquals("0.9", extractMajorAndMinorVersion("0.9.1"))
    }

    @Test
    fun `majorAndMinorVersion for dev version`() {
        assertEquals("0.9", extractMajorAndMinorVersion("0.9.1-dev-123"))
    }

    @Test
    fun `majorAndMinorVersion for two-part version`() {
        assertEquals("1.0", extractMajorAndMinorVersion("1.0"))
    }

    @Test
    fun `majorAndMinorVersion for snapshot`() {
        assertEquals("1.0", extractMajorAndMinorVersion("1.0-SNAPSHOT"))
    }

    @Test
    fun `documentationUrl for release`() {
        assertEquals("https://amper.org/0.9", documentationUrl("0.9.1"))
    }

    @Test
    fun `documentationUrl for dev version`() {
        assertEquals("https://amper.org/dev", documentationUrl("0.9.1-dev-123"))
    }

    @Test
    fun `documentationUrl for snapshot`() {
        assertEquals("https://amper.org/dev", documentationUrl("1.0-SNAPSHOT"))
    }
}
