/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

fun generateUnifiedDiff(
    original: List<String>,
    originalName: String,
    revised: List<String>,
    revisedName: String
): String {
    val patch = DiffUtils.diff(original, revised)
    val diffLines = UnifiedDiffUtils.generateUnifiedDiff(
        originalName, revisedName,
        original,
        patch, 2
    )

    return diffLines.joinToString("\n")
}

fun generateUnifiedDiff(originalFile: Path, revisedFile: Path): String {
    fun Path.normalizedLines() = readText().split("\n").map { it.trimEnd('\r') }

    return generateUnifiedDiff(
        original = originalFile.normalizedLines(),
        originalName = originalFile.absolutePathString(),
        revised = revisedFile.normalizedLines(),
        revisedName = revisedFile.absolutePathString(),
    )
}
