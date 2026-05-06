/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.wrapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class TemplateSubstitutionTest {
    @Test
    fun substituteTemplatePlaceholders_lineEndingsShouldBeUntouched() {
        val result = Template(
            text = "some\r\ntemplate\n",
            name = "test",
        ).substitute(
            macroSubstitutions = emptyMap(),
            templateProvider = { null },
        )

        assertEquals("some\r\ntemplate\n", result)
    }

    @Test
    fun substituteTemplatePlaceholders_replacesPlaceholders_single() {
        val result = Template(
            text = "here is some @PLACEHOLDER@",
            name = "test",
        ).substitute(
            macroSubstitutions = mapOf("PLACEHOLDER" to "value"),
            templateProvider = { null },
        )

        assertEquals("here is some value", result)
    }

    @Test
    fun substituteTemplatePlaceholders_replacesPlaceholders_multiple() {
        val result = Template(
            text = "here is @PLACEHOLDER1@\nand @PLACEHOLDER2@",
            name = "test",
        ).substitute(
            macroSubstitutions = mapOf(
                "PLACEHOLDER1" to "value1",
                "PLACEHOLDER2" to "value2",
            ),
            templateProvider = { null },
        )

        assertEquals("here is value1\nand value2", result)
    }

    @Test
    fun substituteTemplatePlaceholders_failsOnUnreplacedPlaceholders() {
        val error = assertFails {
            Template(
                text = "here is some @PLACEHOLDER@",
                name = "test",
            ).substitute(
                macroSubstitutions = emptyMap(),
                templateProvider = { null },
            )
        }
        assertEquals("macro `PLACEHOLDER` is not defined (requested at char range 13..25 in `test`)", error.message)
    }

    @Test
    fun include_basic() {
        val result = Template(
            text = "before\n@include:included.sh@\nafter",
            name = "main",
        ).substitute(
            macroSubstitutions = emptyMap(),
            templateProvider = { name ->
                if (name == "included.sh") Template(text = "included content", name = name) else null
            },
        )

        assertEquals("before\nincluded content\nafter", result)
    }

    @Test
    fun include_withMacrosInIncludedTemplate() {
        val result = Template(
            text = "wrapper @VERSION@\n@include:common.sh@",
            name = "main",
        ).substitute(
            macroSubstitutions = mapOf("VERSION" to "1.0", "NAME" to "Amper"),
            templateProvider = { name ->
                if (name == "common.sh") Template(text = "hello @NAME@", name = name) else null
            },
        )

        assertEquals("wrapper 1.0\nhello Amper", result)
    }

    @Test
    fun include_nested() {
        val templates = mapOf(
            "level1.sh" to Template(text = "L1[@include:level2.sh@]", name = "level1.sh"),
            "level2.sh" to Template(text = "L2", name = "level2.sh"),
        )
        val result = Template(
            text = "root(@include:level1.sh@)",
            name = "main",
        ).substitute(
            macroSubstitutions = emptyMap(),
            templateProvider = { name -> templates[name] },
        )

        assertEquals("root(L1[L2])", result)
    }

    @Test
    fun include_failsOnMissingTemplate() {
        val error = assertFails {
            Template(
                text = "before @include:missing.sh@ after",
                name = "main",
            ).substitute(
                macroSubstitutions = emptyMap(),
                templateProvider = { null },
            )
        }
        assertEquals("`missing.sh` not found (included at char range 7..26 in `missing.sh`)", error.message)
    }

    @Test
    fun include_failsOnInvalidDirective() {
        val error = assertFails {
            Template(
                text = "before @unknown:foo@ after",
                name = "main",
            ).substitute(
                macroSubstitutions = emptyMap(),
                templateProvider = { null },
            )
        }
        assertEquals("invalid macro directive: `@unknown:foo@`; only `@include:<name>@` is supported", error.message)
    }
}