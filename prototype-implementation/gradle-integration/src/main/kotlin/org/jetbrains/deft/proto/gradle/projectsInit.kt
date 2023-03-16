package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PotatoModuleFileSource
import java.nio.file.Path
import kotlin.io.path.isSameFileAs

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

    // Function to create valid gradle project paths.
    val path2ProjectPath = mutableMapOf<Path, String>()
    fun getProjectPathAndMemoize(currentModulePath: Path): String {
        // Check if we are root module.
        if (currentModulePath.isSameFileAs(rootPath)) return ":"
        // Get previous known project path.
        var previousPath: Path = currentModulePath
        while (!path2ProjectPath.containsKey(previousPath) && !previousPath.isSameFileAs(rootPath))
            previousPath = Path.of("/").resolve(previousPath.subpath(0, previousPath.nameCount - 1))
        val previousNonRootProjectPath = path2ProjectPath[previousPath]
        // Calc current project path, based on file structure.
        val currentProjectId = currentModulePath.fileName.toString()
        val resultProjectPath = if (previousNonRootProjectPath != null) {
            "$previousNonRootProjectPath:$currentProjectId"
        } else {
            ":$currentProjectId"
        }
        // Memoize.
        path2ProjectPath[currentModulePath] = resultProjectPath
        return resultProjectPath
    }

    val pathToModule = mutableMapOf<String, PotatoModuleWrapper>()
    val moduleToPath = mutableMapOf<Path, String>()

    // Go by ascending path length and generate projects.
    sortedByPath.forEach {
        val projectPath = getProjectPathAndMemoize(it.buildDir)
        settings.include(projectPath)
        val project = settings.project(projectPath)
        project.projectDir = it.buildDir.toFile()
        pathToModule[projectPath] = PotatoModuleWrapper(it)
        moduleToPath[it.buildDir] = projectPath
    }

    // Associate gradle projects with modules.
    settings.gradle.projectPathToModule = pathToModule
    settings.gradle.moduleFilePathToProject = moduleToPath
}