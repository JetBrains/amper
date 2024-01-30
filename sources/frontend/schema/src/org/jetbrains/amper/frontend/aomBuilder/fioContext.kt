/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSameFileAs
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readLines


private const val amperModuleFileName = "module.yaml"
private const val amperIgnoreFileName = ".amperignore"
private fun Path.isModuleYaml() = name == amperModuleFileName

private val gradleModuleFiles = setOf("build.gradle.kts", "build.gradle")
private const val gradleDefaultVersionCatalogName = "libs.versions.toml"
private const val gradleDirName = "gradle"

/**
 * Files context used to parse amper modules.
 */
interface FioContext {
    val root: Path

    val rootDir: Path

    /**
     * `.amperignore` file parsed lines as paths, relative to [rootDir].
     */
    val ignorePaths: List<Path>

    /**
     * All amper module files ([amperModuleFileName]), that are found within [rootDir] hierarchy.
     */
    val amperModuleFiles: List<Path>

    /**
     * Parents of [amperModuleFiles].
     */
    val amperModuleDirs: List<Path>

    /**
     * All gradle modules ([gradleModuleFiles]), that are found within [rootDir] hierarchy.
     */
    val gradleModules: Map<Path, DumbGradleModule>

    /**
     * Try to find catalog path for given module or template path.
     */
    fun getCatalogPathFor(file: Path): Path?
}

/**
 * Default files context.
 */
open class DefaultFioContext(
    override val root: Path
) : FioContext {

    override val rootDir: Path by lazy {
        root.takeIf { it.isDirectory() }
            ?: root.parent
            ?: error("Should not call with a rot without parent")
    }

    override val ignorePaths: List<Path> by lazy {
        rootDir.resolve(amperIgnoreFileName)
            .takeIf(Path::exists)
            ?.readLines()
            .orEmpty()
            .map { it.trim() }
            // Ignore comments.
            .filter { !it.startsWith("#") }
            .map { rootDir / it }
    }

    override val amperModuleFiles: List<Path> by lazy {
        Files.walk(rootDir)
            .filter { file -> file.isModuleYaml() && ignorePaths.none { file.startsWith(it) } }
            .collect(Collectors.toList())
    }

    override val amperModuleDirs: List<Path> by lazy { amperModuleFiles.map { it.parent } }

    override val gradleModules: Map<Path, DumbGradleModule> by lazy {
        Files.walk(rootDir)
            .filter { gradleModuleFiles.contains(it.name) }
            .filter { it.parent !in amperModuleDirs }
            .filter { ignorePaths.none { ignorePath -> it.startsWith(ignorePath) } }
            .map { it.parent to DumbGradleModule(it) }
            .collect(Collectors.toList())
            .toMap()
    }

    data class CatalogPathHolder(val path: Path?)
    val myKnownGradleCatalogs = mutableMapOf<Path, CatalogPathHolder>()
    override fun getCatalogPathFor(file: Path) = myKnownGradleCatalogs
        .computeIfAbsent(file) { CatalogPathHolder(it.findGradleCatalog()) }
        .path

    /**
     * Find "libs.versions.toml" in every gradle directory between [this] path and [rootDir]
     * with deeper files being the first.
     */
    private fun Path.findGradleCatalog(): Path? {
        assert(startsWith(rootDir)) {
            "Cannot call with the path($pathString) that is outside of (${rootDir.pathString})"
        }
        val currentDir = takeIf { it.isDirectory() } ?: parent

        // Directories from [this] to [rootDir], both ends including.
        val directories = if (currentDir.isSameFileAs(rootDir)) listOf(currentDir)
        else generateSequence(currentDir) { dir ->
            dir.parent.takeIf { !it.isSameFileAs(rootDir) }
        }.filter { it.isDirectory() }.toList() + rootDir

        return directories.asSequence()
            .map { it / gradleDirName / gradleDefaultVersionCatalogName }
            .mapNotNull { it.takeIf(Path::exists) }
            .filter { ignorePaths.none { ignorePath -> it.startsWith(ignorePath) } }
            .firstOrNull()
    }
}

/**
 * Per-file context.
 */
class ModuleFioContext(
    requiredFile: Path,
    project: Project,
) : DefaultFioContext(project.baseDir.toNioPath()) {

    private val requiredDir =
        if (requiredFile.isDirectory()) requiredFile else requiredFile.parent

    override val amperModuleFiles: List<Path> by lazy {
        Files.list(requiredDir)
            .filter { file -> file.isModuleYaml() && ignorePaths.none { file.startsWith(it) } }
            .collect(Collectors.toList())
    }

    override val gradleModules: Map<Path, DumbGradleModule> by lazy {
        emptyMap()
    }
}
