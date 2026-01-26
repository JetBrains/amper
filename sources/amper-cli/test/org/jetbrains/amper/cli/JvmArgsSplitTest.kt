/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.cli.options.splitArgsHonoringQuotes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmArgsSplitTest {

    @Test
    fun singleArg_shouldBeKeptAsIs() {
        assertEquals(listOf("arg1"), "arg1".splitArgsHonoringQuotes())
        assertEquals(listOf("-Dmy.prop=hello"), "-Dmy.prop=hello".splitArgsHonoringQuotes())
        assertEquals(listOf("-Xmx=4g"), "-Xmx=4g".splitArgsHonoringQuotes())
    }

    @Test
    fun unquotedArgs_shouldBeSplitOnSpaces() {
        assertEquals(listOf("arg1", "arg2"), "arg1 arg2".splitArgsHonoringQuotes())
        assertEquals(listOf("arg1", "arg2", "arg3"), "arg1 arg2 arg3".splitArgsHonoringQuotes())
        assertEquals(listOf("-ea", "-Xmx=4g", "-Dmy.prop=hello"), "-ea -Xmx=4g -Dmy.prop=hello".splitArgsHonoringQuotes())
    }

    @Test
    fun unquotedArgs_multiSpacesShouldBeConsideredAsOneSeparator() {
        assertEquals(listOf("arg1", "arg2"), "arg1   arg2".splitArgsHonoringQuotes())
        assertEquals(listOf("arg1", "arg2", "arg3"), "arg1  arg2 arg3".splitArgsHonoringQuotes())
        assertEquals(listOf("-ea", "-Xmx=4g", "-Dmy.prop=hello"), "-ea    -Xmx=4g   -Dmy.prop=hello".splitArgsHonoringQuotes())
    }

    @Test
    fun unquotedArgs_shouldKeepTheirEscapedSpaces() {
        assertEquals(listOf(" "), """\ """.splitArgsHonoringQuotes())
        assertEquals(listOf(" ", " "), """\  \ """.splitArgsHonoringQuotes())
        assertEquals(listOf("a b c"), """a\ b\ c""".splitArgsHonoringQuotes())
        assertEquals(listOf("a b c", "d"), """a\ b\ c d""".splitArgsHonoringQuotes())
        assertEquals(listOf("a b c", "d e"), """a\ b\ c d\ e""".splitArgsHonoringQuotes())
    }

    @Test
    fun quotedArgs_shouldLoseTheirQuotes() {
        assertEquals(listOf("arg1", "-Dmy.prop=hello"), "\"arg1\" \"-Dmy.prop=hello\"".splitArgsHonoringQuotes())
    }

    @Test
    fun quotedArgs_shouldKeepTheirSpaces() {
        assertEquals(listOf("start end"), "\"start end\"".splitArgsHonoringQuotes())
        assertEquals(listOf("start end", "arg2"), "\"start end\" arg2".splitArgsHonoringQuotes())
        assertEquals(listOf("-Dmy.prop=hello world"), "\"-Dmy.prop=hello world\"".splitArgsHonoringQuotes())
        assertEquals(listOf("-Dmy.prop=hello world"), "-Dmy.prop=\"hello world\"".splitArgsHonoringQuotes())
    }

    @Test
    fun escapedQuotes_shouldBeKeptAsIs() {
        assertEquals(listOf("arg1\"", "\"arg2"), """arg1\" \"arg2""".splitArgsHonoringQuotes())
        assertEquals(listOf("start\"end"), """start\"end""".splitArgsHonoringQuotes())
    }

    @Test
    fun escapedBackslash_shouldBeKeptAsIs() {
        assertEquals(listOf("arg1\\", "\\arg2"), """arg1\\ \\arg2""".splitArgsHonoringQuotes())
    }

    @Test
    fun escapedLetter_shouldBeKeptAsIs() {
        assertEquals(listOf("a", "b"), """\a \b""".splitArgsHonoringQuotes())
    }

    @Test
    fun escapedBackslash_shouldNotEscapeQuotes() {
        assertEquals(listOf("start\\ \\end"), """start\\" \\"end""".splitArgsHonoringQuotes())
    }

    @Test
    fun quotedEmptyArg_shouldBeKept() {
        assertEquals(listOf(""), "\"\"".splitArgsHonoringQuotes())
        assertEquals(listOf("arg1", ""), "arg1 \"\"".splitArgsHonoringQuotes())
        assertEquals(listOf("arg1", "", "arg3"), "arg1 \"\" arg3".splitArgsHonoringQuotes())
        assertEquals(listOf("arg1", "", "arg3"), "arg1  \"\"   arg3".splitArgsHonoringQuotes())
    }

    @Test
    fun escapedSpace_shouldBeKeptAndNotSplitArgs() {
        assertEquals(listOf("start end"), "start\\ end".splitArgsHonoringQuotes())
        assertEquals(listOf("start   far-end"), "start\\ \\ \\ far-end".splitArgsHonoringQuotes())
        assertEquals(listOf("start middle end"), "start\\ middle\\ end".splitArgsHonoringQuotes())
    }

    @Test
    fun unclosedQuote_shouldFail() {
        assertFailsWith<UserReadableError> { "\"".splitArgsHonoringQuotes() }
        assertFailsWith<UserReadableError> { "\"arg1".splitArgsHonoringQuotes() }

        val e3 = assertFailsWith<UserReadableError> { "\"start end".splitArgsHonoringQuotes() }
        assertEquals("""
            Unclosed quote at index 0 in --jvm-args:
            "start end
            ^
        """.trimIndent(), e3.message)

        val e4 = assertFailsWith<UserReadableError> { "\"arg1\" \"arg2".splitArgsHonoringQuotes() }
        assertEquals("""
            Unclosed quote at index 7 in --jvm-args:
            "arg1" "arg2
                   ^
        """.trimIndent(), e4.message)
    }

    @Test
    fun danglingEscape_shouldFail() {
        assertFailsWith<UserReadableError> { "\\".splitArgsHonoringQuotes() }
        assertFailsWith<UserReadableError> { "something\\".splitArgsHonoringQuotes() }
        val e = assertFailsWith<UserReadableError> { "something \\".splitArgsHonoringQuotes() }
        assertEquals("""
            Dangling escape character '\' at the end of the --jvm-args value:
            something \
        """.trimIndent(), e.message)
    }
}
