/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.util.io.isDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.div
import kotlin.io.path.exists
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
     * A list of default gradle version catalog
     * files ([gradleDefaultVersionCatalogName]) for each amper file.
     */
    val amperFiles2gradleCatalogs: Map<Path, List<Path>>
}

/**
 * Default files context.
 */
class DefaultFioContext(
    override val root: Path
) : FioContext {

    override val rootDir by lazy {
        root.takeIf(Path::isDirectory)
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

    override val amperFiles2gradleCatalogs by lazy {
        amperModuleFiles.associateWith { it.findGradleCatalogs() }
    }

    /**
     * Find "libs.versions.toml" in every gradle directory between [this] path and [rootDir]
     * with deeper files being the first.
     */
    private fun Path.findGradleCatalogs(): List<Path> {
        assert(startsWith(rootDir)) {
            "Cannot call with the path($pathString) that is outside of (${rootDir.pathString})"
        }

        // Directories from [this] to [rootDir], both ends including.
        val directories = if (isSameFileAs(rootDir)) listOf(rootDir)
        else generateSequence(this) { dir ->
            dir.parent.takeIf { !it.isSameFileAs(rootDir) }
        }.filter { it.isDirectory() }.toList() + rootDir

        return directories.asSequence()
            .map { it / gradleDirName / gradleDefaultVersionCatalogName }
            .mapNotNull { it.takeIf(Path::exists) }
            .filter { ignorePaths.none { ignorePath -> it.startsWith(ignorePath) } }
            .toList()
    }
}

