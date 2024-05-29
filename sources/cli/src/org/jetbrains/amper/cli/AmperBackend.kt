/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.google.common.io.Files
import io.github.classgraph.ClassGraph
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.use
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.runTasksAndReportOnFailure
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.PlatformUtil
import org.jetbrains.amper.util.substituteTemplatePlaceholders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString

@OptIn(ExperimentalPathApi::class)
class AmperBackend(val context: ProjectContext) {
    private val resolvedModel: Model by lazy {
        with(CliProblemReporterContext()) {
            val model = spanBuilder("loading model")
                .setAttribute("root", context.projectRoot.path.pathString)
                .startSpan().use {
                    when (val result = ModelInit.getModel(context.projectRoot.path)) {
                        is Result.Failure -> throw result.exception
                        is Result.Success -> result.value
                    }
                }

            if (problemReporter.wereProblemsReported()) {
                userReadableError("failed to build tasks graph, refer to the errors above")
            }

            for ((moduleUserReadableName, moduleList) in model.modules.groupBy { it.userReadableName }) {
                if (moduleList.size > 1) {
                    userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in " +
                    moduleList.joinToString(" ") { (it.source as? PotatoModuleFileSource)?.buildFile?.toString() ?: "" })
                }
            }

            model
        }
    }

    private val taskGraph: TaskGraph by lazy {
        ProjectTasksBuilder(context = context, model = resolvedModel).build()
    }

    private val taskExecutor: TaskExecutor by lazy {
        val progress = TaskProgressRenderer(context.terminal, context.backgroundScope)
        TaskExecutor(taskGraph, context.taskExecutionMode, progress)
    }

    /**
     * Called by the 'build' command. Compiles and links all code in the project.
     *
     * If [platforms] is specified, only compilation/linking for those platforms should be run.
     */
    fun build(platforms: Set<Platform>? = null) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }

        val possibleCompilationPlatforms = if (OsFamily.current.isMac) {
            Platform.leafPlatforms
        } else {
            // Apple targets could be compiled only on Mac OS X due to legal obstacles
            Platform.leafPlatforms.filter { !it.isDescendantOf(Platform.APPLE) }.toSet()
        }

        val platformsToCompile: Set<Platform> = platforms ?: possibleCompilationPlatforms
        runBlocking {
            val taskNames = taskGraph
                .tasks
                .filterIsInstance<BuildTask>()
                .filter { platformsToCompile.contains(it.platform) }
                .map { it.taskName }
                .toSet()
            logger.info("Selected tasks to compile: ${taskNames.sortedBy { it.name }.joinToString(" ") { it.name }}")
            taskExecutor.runTasksAndReportOnFailure(taskNames)
        }
    }

    suspend fun runTask(taskName: TaskName) = taskExecutor.runTasksAndReportOnFailure(setOf(taskName))

    fun showTasks() {
        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            context.terminal.println(buildString {
                append("task ${taskName.name}")
                taskGraph.dependencies[taskName]?.let { taskDeps ->
                    append(" -> ${taskDeps.joinToString { it.name }}")
                }
            })
        }
    }

    fun showModules() {
        for (moduleName in resolvedModel.modules.map { it.userReadableName }.sorted()) {
            context.terminal.println(moduleName)
        }
    }

    fun initProject(template: String?) {
        val allTemplateFiles = ClassGraph().acceptPaths("templates").scan().use { scanResult ->
            scanResult.allResources.paths.map { pathString ->
                check(pathString.startsWith("templates/")) {
                    "Resource path must start with templates/: $pathString"
                }
                pathString
            }
        }

        val allTemplateNames = allTemplateFiles.map {
            Path.of(it).getName(1).pathString
        }.distinct().sorted()

        if (template == null) {
            userReadableError(
                "Please specify a template (template name substring is sufficient).\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}")
        }

        val root = context.projectRoot.path
        if (root.exists() && !root.isDirectory()) {
            userReadableError("Project root is not a directory: $root")
        }

        val matchedTemplates = allTemplateNames.filter {
            it.contains(template, ignoreCase = true)
        }

        if (matchedTemplates.isEmpty()) {
            userReadableError(
                "No templates were found matching '$template'\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}"
            )
        }
        if (matchedTemplates.size > 1) {
            userReadableError(
                "Multiple templates (${
                    matchedTemplates.sorted().joinToString(" ")
                }) were found matching '$template'\n\n" +
                        "Available templates: ${allTemplateNames.joinToString(" ")}"
            )
        }

        val matchedTemplate = matchedTemplates.single()
        context.terminal.println("Extracting template '$matchedTemplate' to $root")

        val resourcePrefix = "templates/$matchedTemplate/"
        val templateFiles = allTemplateFiles
            .filter { it.startsWith(resourcePrefix) }
            .map { it to it.removePrefix(resourcePrefix) }
        check(templateFiles.isNotEmpty()) {
            "No files was found for template '$matchedTemplate'. All template files:\n" +
                    allTemplateFiles.joinToString("\n")
        }

        checkTemplateFilesConflicts(templateFiles, root)

        root.createDirectories()
        for ((resourceName, relativeName) in templateFiles) {
            val path = root.resolve(relativeName)
            path.parent.createDirectories()
            javaClass.classLoader.getResourceAsStream(resourceName)!!.use { stream ->
                Files.asByteSink(path.toFile()).writeFrom(stream)
            }
        }
        writeWrappers(root)

        context.terminal.println("Project template successfully instantiated to $root")
        context.terminal.println()
        val exe = if (OsFamily.current.isWindows) "amper.bat build" else "./amper build"
        context.terminal.println("Now you may build your project with '$exe' or open this folder in IDE with Amper plugin")
    }

    private data class AmperWrapper(
        val fileName: String,
        val resourceName: String,
        val executable: Boolean,
        val windowsLineEndings: Boolean,
    )

    private val wrappers = listOf(
        AmperWrapper(fileName = "amper", resourceName = "wrappers/amper.template.sh", executable = true, windowsLineEndings = false),
        AmperWrapper(fileName = "amper.bat", resourceName = "wrappers/amper.template.bat", executable = false, windowsLineEndings = true),
    )

    private fun writeWrappers(root: Path) {
        val sha256: String? = System.getProperty("amper.wrapper.dist.sha256")
        if (sha256.isNullOrEmpty()) {
            logger.warn("Amper was not run from amper wrapper, skipping generating wrappers for $root")
            return
        }

        if (AmperBuild.isSNAPSHOT) {
            logger.warn("Amper was compiled from sources in dev environment, skipping generating wrappers for $root")
            return
        }

        for (w in wrappers) {
            val path = root.resolve(w.fileName)

            substituteTemplatePlaceholders(
                input = javaClass.classLoader.getResourceAsStream(w.resourceName)!!.use { it.readAllBytes() }.decodeToString(),
                outputFile = path,
                placeholder = "@",
                values = listOf(
                    "AMPER_VERSION" to AmperBuild.BuildNumber,
                    "AMPER_DIST_SHA256" to sha256,
                ),
                outputWindowsLineEndings = w.windowsLineEndings,
            )

            if (w.executable) {
                val rc = path.toFile().setExecutable(true)
                check(rc) {
                    "Unable to make file executable: $rc"
                }
            }
        }
    }

    private fun checkTemplateFilesConflicts(templateFiles: List<Pair<String, String>>, root: Path) {
        val filesToCheck = templateFiles.map { it.second }
        val alreadyExistingFiles = filesToCheck.filter { root.resolve(it).exists() }
        if (alreadyExistingFiles.isNotEmpty()) {
            userReadableError(
                "Files already exist in the project root:\n" +
                        alreadyExistingFiles.joinToString("\n").prependIndent("  ")
            )
        }
    }

    fun publish(modules: Set<String>?, repositoryId: String) {
        require(modules == null || modules.isNotEmpty())

        if (modules != null) {
            for (moduleName in modules) {
                val module = resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                    ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\n" +
                                "Available modules: ${availableModulesString()}")

                if (module.mavenRepositories.any { it.id == repositoryId }) {
                    userReadableError("Module '$moduleName' does not have repository with id '$repositoryId'")
                }
            }
        }

        val publishTasks = taskGraph.tasks
            .filterIsInstance<PublishTask>()
            .filter { it.targetRepository.id == repositoryId }
            .filter { modules == null || modules.contains(it.module.userReadableName) }
            .map { it.taskName }
            .toSet()

        if (publishTasks.isEmpty()) {
            userReadableError("No publish tasks were found for specified module and repository filters")
        }

        context.terminal.println("Tasks that will be executed:\n" +
            publishTasks.sorted().joinToString("\n"))

        runBlocking {
            taskExecutor.runTasksAndReportOnFailure(publishTasks)
        }
    }

    fun test(moduleName: String? = null, platforms: Set<Platform>? = null) {
        require(platforms == null || platforms.isNotEmpty())

        if (moduleName != null && resolvedModel.modules.none { it.userReadableName == moduleName }) {
            userReadableError("Unable to resolve module by name '$moduleName'.\n\n" +
                    "Available modules: ${availableModulesString()}")
        }

        if (platforms != null) {
            for (platform in platforms) {
                if (!PlatformUtil.platformsMayRunOnCurrentSystem.contains(platform)) {
                    userReadableError(
                        "Unable to run platform '$platform' on current system.\n\n" +
                                "Available platforms on current system: " +
                                PlatformUtil.platformsMayRunOnCurrentSystem.map { it.name }.sorted().joinToString(" ")
                    )
                }
            }
        }

        val testTasks = taskGraph.tasks
            .filterIsInstance<TestTask>()
            .filter { moduleName == null || moduleName == it.module.userReadableName }
            .filter { platforms == null || platforms.contains(it.platform) }
            .map { it.taskName }
            .toSet()

        if (testTasks.isEmpty()) {
            userReadableError("No test tasks were found for specified module and platform filters")
        }

        runBlocking {
            taskExecutor.runTasksAndReportOnFailure(testTasks)
        }
    }

    fun runApplication(moduleName: String? = null, platform: Platform? = null, buildType: BuildType? = BuildType.Debug) {
        val moduleToRun = if (moduleName != null) {
            resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\nAvailable modules: ${availableModulesString()}")
        } else {
            val candidates = resolvedModel.modules.filter { it.type.isApplication() }
            when {
                candidates.isEmpty() -> userReadableError("No modules in the project with application type")
                candidates.size > 1 ->
                    userReadableError(
                        "There are several application modules in the project. Please specify one with '--module' argument.\n\n" +
                                "Available application modules: ${candidates.map { it.userReadableName }.sorted()}"
                    )

                else -> candidates.single()
            }
        }

        val moduleRunTasks = taskGraph.tasks.filterIsInstance<RunTask>()
            .filter { it.module == moduleToRun }
            .filter { it.buildType == buildType }
        if (moduleRunTasks.isEmpty()) {
            userReadableError("No run tasks are available for module '${moduleToRun.userReadableName}'")
        }

        fun availablePlatformsForModule() = moduleRunTasks.map { it.platform.pretty }.sorted().joinToString(" ")

        val task: RunTask = if (platform == null) {
            if (moduleRunTasks.size == 1) {
                moduleRunTasks.single()
            } else {
                userReadableError("""
                    Multiple platforms are available to run in module '${moduleToRun.userReadableName}'.
                    Please specify one with '--platform' argument.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
            }
        } else {
            moduleRunTasks.firstOrNull { it.platform == platform }
                ?: userReadableError("""
                    Platform '${platform.pretty}' is not found for module '${moduleToRun.userReadableName}'.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
        }

        runBlocking { runTask(task.taskName) }
    }

    private fun availableModulesString() =
        resolvedModel.modules.map { it.userReadableName }.sorted().joinToString(" ")

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
