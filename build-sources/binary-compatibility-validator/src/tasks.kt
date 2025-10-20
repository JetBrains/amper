/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.bcv

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import kotlinx.validation.api.dump
import kotlinx.validation.api.extractAnnotatedPackages
import kotlinx.validation.api.filterOutAnnotated
import kotlinx.validation.api.filterOutNonPublic
import kotlinx.validation.api.loadApiFromJvmClasses
import kotlinx.validation.api.replaceDots
import kotlinx.validation.api.retainExplicitlyIncludedIfDeclared
import org.jetbrains.amper.CompilationArtifact
import org.jetbrains.amper.Input
import org.jetbrains.amper.Output
import org.jetbrains.amper.TaskAction
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

@TaskAction
fun kotlinApiDumpTask(
    @Input builtApiFile: Path,
    @Output projectApiFile: Path,
) {
    builtApiFile.copyTo(
        projectApiFile.createParentDirectories(),
        overwrite = true,
    )
}

@TaskAction
fun kotlinApiCompareTask(
    moduleName: String,
    @Input builtApiFile: Path,
    @Input(inferTaskDependency = false) projectApiFile: Path,
) {
    if (!projectApiFile.exists()) {
        error(
            "Expected file with API declarations '${projectApiFile}' does not exist.\n" +
                    "Please ensure that ':apiDump' was executed in order to get " +
                    "an API dump to compare the build against"
        )
    }

    val diffSet = mutableSetOf<String>()
    val diff = compareFiles(
        checkFile = projectApiFile,
        builtFile = builtApiFile,
    )
    if (diff != null) diffSet.add(diff)
    if (diffSet.isNotEmpty()) {
        val diffText = diffSet.joinToString("\n\n")
        error(
            "API check failed for module $moduleName.\n$diffText\n\n" +
                    "You can run :$moduleName:apiBuild@binary-compatibility-validator " +
                    "task to overwrite API declarations"
        )
    }
}

private fun compareFiles(checkFile: Path, builtFile: Path): String? {
    val checkText = checkFile.readText()
    val builtText = builtFile.readText()

    // We don't compare a full text because newlines on Windows & Linux/macOS are different
    val checkLines = checkText.lines()
    val builtLines = builtText.lines()
    if (checkLines == builtLines)
        return null

    val patch = DiffUtils.diff(checkLines, builtLines)
    val diff =
        UnifiedDiffUtils.generateUnifiedDiff(checkFile.toString(), builtFile.toString(), checkLines, patch, 3)
    return diff.joinToString("\n")
}

@TaskAction
fun kotlinApiBuildTask(
    settings: Settings,
    @Input inputJar: CompilationArtifact,
    @Output outputApiFile: Path,
) {
    outputApiFile.createParentDirectories()

    val signatures = JarFile(inputJar.artifact.toFile()).use { it.loadApiFromJvmClasses() }

    val publicPackagesNames =
        signatures.extractAnnotatedPackages(settings.publicMarkers.map(::replaceDots).toSet())
    val ignoredPackagesNames =
        signatures.extractAnnotatedPackages(settings.nonPublicMarkers.map(::replaceDots).toSet())

    val filteredSignatures = signatures
        .retainExplicitlyIncludedIfDeclared(
            settings.publicPackages + publicPackagesNames, settings.publicClasses, settings.publicMarkers
        )
        .filterOutNonPublic(settings.ignoredPackages + ignoredPackagesNames, settings.ignoredClasses)
        .filterOutAnnotated(settings.nonPublicMarkers.map(::replaceDots).toSet())

    outputApiFile.bufferedWriter().use { writer ->
        filteredSignatures.dump(writer)
    }
}