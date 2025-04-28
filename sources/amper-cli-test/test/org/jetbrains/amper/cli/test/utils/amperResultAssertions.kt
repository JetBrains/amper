/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.utils

import org.jetbrains.amper.cli.test.AmperCliTestBase
import org.slf4j.event.Level
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun AmperCliTestBase.AmperCliResult.assertSomeStdoutLineContains(text: String) {
    assertTrue("No line in stdout contains the text '$text':\n" + stdout.trim()) {
        stdout.lineSequence().any { text in it }
    }
}

fun AmperCliTestBase.AmperCliResult.assertStdoutContains(text: String) {
    assertTrue("Stdout does not contain the text '$text':\n" + stdout.trim()) {
        text in stdout
    }
}

fun AmperCliTestBase.AmperCliResult.assertStdoutContains(text: String, expectedOccurrences: Int) {
    val actualOccurrences = Regex.fromLiteral(text).findAll(stdout).count()
    assertEquals(expectedOccurrences, actualOccurrences, "Stdout should contain the text '$text' $expectedOccurrences time(s), but got $actualOccurrences:\n" + stdout.trim())
}

fun AmperCliTestBase.AmperCliResult.assertStdoutDoesNotContain(text: String) {
    assertFalse("Stdout should not contain the text '$text':\n" + stdout.trim()) {
        text in stdout
    }
}

fun AmperCliTestBase.AmperCliResult.assertStderrContains(text: String) {
    assertTrue("Stderr does not contain the text '$text':\n" + stderr.trim()) {
        text in stderr
    }
}

fun AmperCliTestBase.AmperCliResult.assertStdoutContainsLine(expectedLine: String, nOccurrences: Int = 1) {
    val suffix = if (nOccurrences > 1) " $nOccurrences times" else " once"
    val count = stdout.lines().count { it == expectedLine }
    assertTrue("stdout should contain line '$expectedLine'$suffix (got $count occurrences)") {
        count == nOccurrences
    }
}

fun AmperCliTestBase.AmperCliResult.assertLogStartsWith(msgPrefix: String, level: Level) {
    assertTrue("Log message with level=$level and starting with '$msgPrefix' was not found") {
        val logs = if (level >= Level.INFO) infoLogs else debugLogs
        logs.any { it.level == level && it.message.startsWith(msgPrefix) }
    }
}

fun AmperCliTestBase.AmperCliResult.assertLogContains(text: String, level: Level) {
    assertTrue("Log message with level=$level and containing '$text' was not found") {
        val logs = if (level >= Level.INFO) infoLogs else debugLogs
        logs.any { it.level == level && text in it.message }
    }
}