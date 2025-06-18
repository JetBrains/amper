/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.golden

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.test.generateUnifiedDiff
import org.junit.jupiter.api.AssertionFailureBuilder
import org.opentest4j.AssertionFailedError
import org.opentest4j.FileInfo
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

fun GoldenTest.readContentsAndReplace(
    expectedPath: Path,
    base: Path,
): String {
    val buildDir = buildDir().normalize().toString()
    val potDir = expectedPath.toAbsolutePath().normalize().parent.toString()
    val testProcessDir = Path(".").normalize().absolutePathString()
    val testResources = Path(".").resolve(base).normalize().absolutePathString()

    // This is the actual check.
    if (!expectedPath.exists()) expectedPath.writeText("")
    val resourceFileText = expectedPath.readText()
    return resourceFileText
        .replace("{{ buildDir }}", buildDir)
        .replace("{{ potDir }}", this.buildDir().parent.relativize(Path(potDir)).toString())
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

private fun String.adjustEol(expectedFilePath: Path): String {
    // On Windows with core.crlf = auto setting, we get '\r' in all text files
    // Let's handle it transparently to developers
    val crInExpectedFile = expectedFilePath.takeIf { it.exists() }?.readText()?.contains("\r") ?: false
    return if (crInExpectedFile && !this.contains("\r")) this.replace("\n", "\r\n") else this
}

fun Path.actualFilePath(): Path = parent.resolve(this.fileName.name + ".tmp")

private fun String.replaceVersionsWithVariables(): String =
    replaceArtifactFilenames(
        filePrefix = "kotlin-stdlib",
        version = UsedVersions.kotlinVersion,
        versionVariableName = "kotlinVersion",
    )
        .replaceCoordinateVersionWithReference(
            groupPrefix = "org.jetbrains.kotlin",
            artifactPrefix = "kotlin-",
            version = UsedVersions.kotlinVersion,
            versionVariableName = "kotlinVersion",
        )
        .replaceCoordinateVersionWithReference(
            groupPrefix = "org.jetbrains.compose",
            artifactPrefix = "",
            version = UsedVersions.composeVersion,
            versionVariableName = "composeDefaultVersion",
        )

private fun String.replaceArtifactFilenames(
    filePrefix: String,
    version: String,
    versionVariableName: String,
): String = replace(Regex("""${Regex.escape(filePrefix)}.*-${Regex.escape(version)}\.(jar|aar|klib)""")) {
    it.value.replace(version, "#$versionVariableName")
}
private fun String.replaceCoordinateVersionWithReference(
    groupPrefix: String,
    artifactPrefix: String,
    version: String,
    versionVariableName: String,
): String = replace(Regex("""${Regex.escape(groupPrefix)}[^:]*:${Regex.escape(artifactPrefix)}[^:]*:([\w.\-]+ -> )?${Regex.escape(version)}""")) {
    it.value.replace(version, "#$versionVariableName")
}
