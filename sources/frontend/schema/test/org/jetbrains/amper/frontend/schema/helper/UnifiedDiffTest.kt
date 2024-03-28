/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedDiffTest {
    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun smoke() {
        val actualFile = tempDir.resolve("1.actual")
        val expectedFile = tempDir.resolve("1.expected")

        expectedFile.writeText("""
            1
            2
            3
            4
        """.trimIndent())

        actualFile.writeText("""
            1
            3
            4
            5
        """.trimIndent())

        assertEquals("""
            --- $expectedFile
            +++ $actualFile
            @@ -1,4 +1,4 @@
             1
            -2
             3
             4
            +5
        """.trimIndent(), generateUnifiedDiff(expectedFile, actualFile))
    }
}
