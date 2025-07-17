/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun GoldenTest.readContentsAndReplace(
    expectedPath: Path,
    base: Path,
): String {
    val buildDirPath = buildDir()
    val buildDir = buildDirPath.normalize().toString()
    val potDir = expectedPath.toAbsolutePath().normalize().parent.toString()
    val testProcessDir = Path(".").normalize().absolutePathString()
    val testResources = Path(".").resolve(base).normalize().absolutePathString()

    // This is the actual check.
    if (!expectedPath.exists()) expectedPath.writeText("")
    val resourceFileText = expectedPath.readText()
    return resourceFileText
        .replace("{{ buildDir }}", buildDir)
        .replace("{{ potDir }}", buildDirPath.parent.relativize(Path(potDir)).toString())
        .replace("{{ testProcessDir }}", testProcessDir)
        .replace("{{ testResources }}", testResources)
        .replace("{{ fileSeparator }}", File.separator)
}

fun String.trimTrailingWhitespacesAndEmptyLines(): String {
    return lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n") { it.trimEnd() }
}
