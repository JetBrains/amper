/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.processes.ProcessResult
import kotlin.test.assertTrue

fun ProcessResult.assertStdoutContainsLine(expectedLine: String, nOccurrences: Int = 1) {
    val suffix = if (nOccurrences > 1) " $nOccurrences times" else " once"
    val count = stdout.lines().count { it == expectedLine }
    assertTrue("stdout should contain line '$expectedLine'$suffix (got $count occurrences)") {
        count == nOccurrences
    }
}
