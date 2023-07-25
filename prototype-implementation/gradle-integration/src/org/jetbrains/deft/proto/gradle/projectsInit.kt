package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import org.jetbrains.kotlin.konan.file.File
import java.nio.file.Path
import kotlin.io.path.isSameFileAs
import kotlin.io.path.relativeTo

/**
 * Create gradle project for every [PotatoModule] and fill
 * [projectPathToModule] and [moduleFilePathToProject].
 */
fun initProjects(settings: Settings, model: Model) {
    checkCompatibility(model.modules)
    doInitProjects(settings, settings.rootDir.toPath(), model.modules)
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
    @Suppress("NAME_SHADOWING")
    val rootPath = rootPath.normalize().toAbsolutePath()
    val sortedByPath = modules.sortedBy { it.buildFile }
    val createdGradlePaths = mutableSetOf<String>()

    // Fallback if no modules.
    if (sortedByPath.isEmpty()) return

    // Need to create root project if no module reside in root.
    if (!sortedByPath[0].buildDir.isSameFileAs(rootPath)) {
        createdGradlePaths.add(":")
        settings.include(":")
        settings.project(":").projectDir = rootPath.toFile()
    }

    fun Path.toGradlePath() = ":" + relativeTo(rootPath).toString().replace(File.separator, ":")

    // Go by ascending path length and generate projects.
    sortedByPath.forEach {
        val currentPath = it.buildDir.normalize().toAbsolutePath()
        val projectPath = if (currentPath.isSameFileAs(rootPath)) {
            createdGradlePaths.add(":")
            ":"
        } else {
            // Do include intermediate projects for Gradle correct behaviour.
            var previous = currentPath.parent
            while (!rootPath.isSameFileAs(previous) && !createdGradlePaths.contains(previous.toGradlePath())) {
                settings.include(previous.toGradlePath())
                previous = previous.parent
            }
            currentPath.toGradlePath()
        }

        settings.include(projectPath)
        val project = settings.project(projectPath)
        project.projectDir = it.buildDir.toFile()
        settings.gradle.projectPathToModule[projectPath] = PotatoModuleWrapper(it)
        settings.gradle.moduleFilePathToProject[it.buildDir] = projectPath
    }
}