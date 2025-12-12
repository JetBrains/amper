/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import org.jetbrains.amper.system.info.OsFamily
import java.nio.file.Path
import kotlin.io.path.exists

fun String.trimTrailingWhitespacesAndEmptyLines(): String {
    return lines()
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n") { it.trimEnd() }
}

/**
 * Resolves the given file name inside the [Path].
 * Tries to find a platform-specific variant of a golden file nearby
 * (a file with the same name, but with additional platform-specific suffix at the end).
 *
 * @return platform-specific golden file path if it exists, or the original one if not found.
 */
fun Path.goldenFileOsAware(goldenFileBaseName: String): Path {
    val osSuffix = when {
        OsFamily.current.isWindows -> "-windows"
        OsFamily.current.isMac -> "-mac"
        OsFamily.current.isLinux -> "-linux"
        else -> ""
    }
    val goldenFilePlatformSpecificName =
        goldenFileBaseName.substringBeforeLast(".") +
                osSuffix + "." +
                goldenFileBaseName.substringAfterLast(".")

    val goldenFile = resolve(goldenFilePlatformSpecificName)
        .takeIf { it.exists() }
        ?: resolve(goldenFileBaseName)

    return goldenFile
}
