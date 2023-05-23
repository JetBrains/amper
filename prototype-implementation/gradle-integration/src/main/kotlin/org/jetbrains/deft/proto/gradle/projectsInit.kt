package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import java.nio.file.Path
import kotlin.io.path.isSameFileAs
import kotlin.io.path.relativeTo

/**
 * Create gradle project for every [PotatoModule] and fill
 * [projectPathToModule] and [moduleFilePathToProject].
 */
fun initProjects(settings: Settings, model: Model) {
    val absoluteRootPath = settings.rootDir.toPath().toAbsolutePath()
    checkCompatibility(model.modules)
    doInitProjects(settings, absoluteRootPath, model.modules)
}

private fun checkCompatibility(modules: List<PotatoModule>) {
    val nonFileSource = modules.filter { it.source !is PotatoModuleFileSource }
    if (nonFileSource.isNotEmpty())
        error("Non file build source is not supported. Modules with non file source: " +
                nonFileSource.joinToString { it.userReadableName })
}

/**
 * Initialize gradle [Project]s.
 *
 * Assumptions:
 *  - every path in model is absolute
 *  - every module has file source
 */
private fun doInitProjects(
    settings: Settings,
    rootPath: Path,
    modules: List<PotatoModule>
) {
    val sortedByPath = modules.sortedBy { it.buildFile }

    // Fallback if no modules.
    if (sortedByPath.isEmpty()) return

    // Need to create root project if no module reside in root.
    if (sortedByPath[0] != rootPath) {
        settings.include(":")
        settings.project(":").projectDir = rootPath.toFile()
    }

    // Go by ascending path length and generate projects.
    sortedByPath.forEach {
        val currentPath = it.buildDir
        val projectPath = if (currentPath.isSameFileAs(rootPath)) {
            ":"
        } else {
            ":" + currentPath.relativeTo(rootPath).toString().replace("/", ":")
        }
        settings.include(projectPath)
        val project = settings.project(projectPath)
        project.projectDir = it.buildDir.toFile()
        settings.gradle.projectPathToModule[projectPath] = PotatoModuleWrapper(it)
        settings.gradle.moduleFilePathToProject[it.buildDir] = projectPath
    }
}