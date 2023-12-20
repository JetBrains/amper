/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines


private const val amperModuleFileName = "module.yaml"
private const val amperIgnoreFileName = ".amperignore"
private fun Path.isModuleYaml() = name == amperModuleFileName

/**
 * Walk all amper module files down the directory tree.
 */
fun Path.findAmperModuleFiles(toIgnore: List<Path>): List<Path> {
    return Files.walk(this)
        .filter { file -> file.isModuleYaml() && toIgnore.none { file.startsWith(it) } }
        .collect(Collectors.toList())
}

/**
 * Try to find `.amperignore` file and read ignores from it.
 */
fun Path.parseAmperIgnorePaths() = resolve(amperIgnoreFileName)
    .takeIf { it.exists() }
    ?.readLines()
    .orEmpty()
    .map { it.trim() }
    // Ignore comments.
    .filter { !it.startsWith("#") }
    .map { resolve(it) }

/**
 * Find gradle files, and associate respective [DumbGradleModule] by their directory paths.
 */
fun Path.findGradleModules(
    toIgnore: List<Path>,
    amperModuleFiles: List<Path>,
): Map<Path, DumbGradleModule> {
    val amperModuleDirs = amperModuleFiles.map { it.parent }
    return Files.walk(this)
        .filter { setOf("build.gradle.kts", "build.gradle").contains(it.name) }
        .filter { it.parent !in amperModuleDirs }
        .filter { toIgnore.none { ignorePath -> it.startsWith(ignorePath) } }
        .map { it.parent to DumbGradleModule(it) }
        .collect(Collectors.toList())
        .toMap()
}