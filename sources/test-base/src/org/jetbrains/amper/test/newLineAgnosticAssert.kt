/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.junit.jupiter.api.AssertionFailureBuilder
import org.opentest4j.FileInfo
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun assertEqualsIgnoreLineSeparator(expectedContent: String, actualContent: String, originalFile: Path) {
    val actualFile = originalFile.parent.resolve(originalFile.name + ".tmp")

    val expectedNormalized = expectedContent.normalizeLineSeparators()
    val actualNormalized = actualContent.normalizeLineSeparators()

    if (expectedNormalized != actualNormalized) {
        // On Windows with core.crlf = auto setting, we get '\r' in all text files
        // Let's handle it transparently to developers
        val crInExpectedFile = originalFile.readText().contains("\r")
        actualFile.writeText(if (crInExpectedFile) actualNormalized.replace("\n", "\r\n") else actualNormalized)

        AssertionFailureBuilder.assertionFailure()
            .message("Comparison failed:\n${generateUnifiedDiff(originalFile, actualFile)}")
            .expected(FileInfo(originalFile.absolutePathString(), originalFile.readBytes()))
            .actual(FileInfo(actualFile.absolutePathString(), actualFile.readBytes()))
            .buildAndThrow()
    } else {
        actualFile.deleteIfExists()
    }
}

fun String.normalizeLineSeparators() = replace("\r", "")
