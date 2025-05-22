/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.junit.jupiter.api.AssertionFailureBuilder
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines

/**
 * Asserts that the given [actual] list matches the [expected] list, and generates a diff in the unified diff format in
 * case of failure, for better readability.
 */
fun assertEqualsWithDiff(expected: List<String>, actual: List<String>, message: String = "Comparison failed") {
    if (expected.filter { it.isNotEmpty() } != actual.filter { it.isNotEmpty() }) {
        AssertionFailureBuilder.assertionFailure()
            .message("$message. Diff:\n${generateUnifiedDiff(expected, "expected", actual, "actual")}")
            .expected(expected)
            .actual(actual)
            .buildAndThrow()
    }
}

/**
 * Generates a user-readable diff between the given [original] and [revised] lines in the Unified Diff format.
 */
fun generateUnifiedDiff(
    original: List<String>,
    originalName: String,
    revised: List<String>,
    revisedName: String
): String {
    val patch = DiffUtils.diff(original, revised)
    val diffLines = UnifiedDiffUtils.generateUnifiedDiff(originalName, revisedName, original, patch, 2)
    return diffLines.joinToString("\n")
}

/**
 * Generates a user-readable diff between the given [originalFile] and [revisedFile] in the Unified Diff format.
 */
fun generateUnifiedDiff(originalFile: Path, revisedFile: Path): String = generateUnifiedDiff(
    original = originalFile.readLines(),
    originalName = originalFile.absolutePathString(),
    revised = revisedFile.readLines(),
    revisedName = revisedFile.absolutePathString(),
)
