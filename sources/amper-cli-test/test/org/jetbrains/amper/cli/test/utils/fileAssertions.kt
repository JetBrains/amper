/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test.utils

import org.jetbrains.amper.test.generateUnifiedDiff
import org.junit.jupiter.api.AssertionFailureBuilder
import org.opentest4j.FileInfo
import java.nio.file.Path
import kotlin.collections.joinToString
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that the directory at this [Path] contains all the files at the given [expectedRelativePaths].
 */
internal fun Path.assertContainsRelativeFiles(vararg expectedRelativePaths: String) {
    val actualFiles = walk()
        .onEach { assertTrue(it.fileSize() > 0, "File should not be empty: $it") }
        .map { it.relativeTo(this).joinToString("/") }
        .sorted()
        .toList()
    // comparing multi-line strings instead of lists for easier comparison of test failures
    assertEquals(expectedRelativePaths.joinToString("\n"), actualFiles.joinToString("\n"))
}

/**
 * Asserts that the content of the file located at the [actual] path matches the contents of the one at [expected].
 */
internal fun assertFileContentEquals(expected: Path, actual: Path) {
    if (!expected.readBytes().contentEquals(actual.readBytes())) {
        AssertionFailureBuilder.assertionFailure()
            .message("Comparison failed:\n${generateUnifiedDiff(expected, actual)}")
            .expected(FileInfo(expected.absolutePathString(), expected.readBytes()))
            .actual(FileInfo(actual.absolutePathString(), actual.readBytes()))
            .buildAndThrow()
    }
}
