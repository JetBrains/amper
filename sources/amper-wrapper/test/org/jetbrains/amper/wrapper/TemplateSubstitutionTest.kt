/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TemplateSubstitutionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun substituteTemplatePlaceholders_lineEndingsShouldBeUntouched() {
        val outputFile = tempDir.resolve("output")

        substituteTemplatePlaceholders(
            input = "some\r\ntemplate\n",
            outputFile = outputFile,
            replacementRules = emptyList(),
        )

        assertEquals("some\r\ntemplate\n", outputFile.readText())
    }

    @Test
    fun substituteTemplatePlaceholders_replacesPlaceholders_single() {
        val outputFile = tempDir.resolve("output")

        substituteTemplatePlaceholders(
            input = "here is some @PLACEHOLDER@",
            outputFile = outputFile,
            replacementRules = listOf("@PLACEHOLDER@" to "value"),
        )

        assertEquals("here is some value", outputFile.readText())
    }

    @Test
    fun substituteTemplatePlaceholders_replacesPlaceholders_multiple() {
        val outputFile = tempDir.resolve("output")

        substituteTemplatePlaceholders(
            input = "here is @PLACEHOLDER1@\nand @PLACEHOLDER2@",
            outputFile = outputFile,
            replacementRules = listOf(
                "@PLACEHOLDER1@" to "value1",
                "@PLACEHOLDER2@" to "value2",
            ),
        )

        assertEquals("here is value1\nand value2", outputFile.readText())
    }

    @Test
    fun substituteTemplatePlaceholders_failsOnUnreplacedPlaceholders() {
        val outputFile = tempDir.resolve("output")

        assertFails {
            substituteTemplatePlaceholders(
                input = "here is some @PLACEHOLDER@",
                outputFile = outputFile,
                replacementRules = emptyList(),
            )
        }
    }
}