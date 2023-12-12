/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.isModuleYaml
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines


/**
 * Walk all amper module files down the directory tree.
 */
fun Path.findAmperModuleFiles(): List<Path> {
    val toIgnore = parseAmperIgnorePaths()
    return Files.walk(root)
        .filter { file -> file.isModuleYaml() && toIgnore.none { file.startsWith(it) } }
        .collect(Collectors.toList())
}

private const val amperModuleFileName = "module.yaml"
private fun Path.isModuleYaml() = name == amperModuleFileName

/**
 * Try to find `.amperignore` file and read ignores from it.
 */
private fun Path.parseAmperIgnorePaths() = resolve(".amperignore")
    .takeIf { it.exists() }
    ?.readLines()
    .orEmpty()
    .map { it.trim() }
    // Ignore comments.
    .filter { !it.startsWith("#") }
    .map { parent.resolve(it) }