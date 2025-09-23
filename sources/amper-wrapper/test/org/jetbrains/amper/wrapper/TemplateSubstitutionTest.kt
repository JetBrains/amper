/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateSubstitutionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun substituteTemplatePlaceholders_lineEndingsShouldBeUntouched() {
        val outputFile = tempDir.resolve("output")

        substituteTemplatePlaceholders(
            input = "some\r\ntemplate\n",
            outputFile = outputFile,
            placeholder = "@",
            values = emptyList(),
        )

        assertEquals("some\r\ntemplate\n", outputFile.readText())
    }
}