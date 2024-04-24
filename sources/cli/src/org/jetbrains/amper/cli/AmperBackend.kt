/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.google.common.io.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.engine.runTasksAndReportOnFailure
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.CompileTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.PlatformUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

@OptIn(ExperimentalPathApi::class)
class AmperBackend(val context: ProjectContext, private val lifetime: CoroutineScope = GlobalScope) {
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
        val progress = TaskProgressRenderer(context.terminal, lifetime)
        TaskExecutor(taskGraph, context.taskExecutionMode, progress)
    }

    fun clean() {
        val rootsToClean = listOf(context.buildOutputRoot.path, context.projectTempRoot.path)
        for (path in rootsToClean) {
            if (path.exists()) {
                logger.info("Deleting $path")
                path.deleteRecursively()
            }
        }
    }

    fun compile(platforms: Set<Platform>? = null) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }

        val platformsToCompile: Set<Platform> = platforms ?: Platform.leafPlatforms
        runBlocking {
            val taskNames = taskGraph
                .tasks
                .filterIsInstance<CompileTask>()
                .filter { platformsToCompile.contains(it.platform) }
                .map { it.taskName }
                .sortedBy { it.name }
            logger.info("Selected tasks to compile: ${taskNames.joinToString(" ") { it.name }}")
            taskExecutor.runTasksAndReportOnFailure(taskNames)
        }
    }

    suspend fun runTask(taskName: TaskName) = taskExecutor.runTasksAndReportOnFailure(listOf(taskName))

    fun showTasks() {
        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            print("task ${taskName.name}")
            taskGraph.dependencies[taskName]?.let { taskDeps ->
                print(" -> ${taskDeps.joinToString { it.name }}")
            }
            println()
        }
    }

    fun showModules() {
        for (moduleName in resolvedModel.modules.map { it.userReadableName }.sorted()) {
            println(moduleName)
        }
    }

    private val projectTemplates: Map<String, String> = mapOf(
        "multiplatform-cli" to "templates/multiplatform-cli.zip"
    )

    fun newProject(template: String?) {
        val availableTemplates = projectTemplates.keys.sorted().joinToString(" ")

        if (template == null) {
            userReadableError("Please specify a template (template name substring is sufficient).\n\n" +
            "Available templates: $availableTemplates")
        }

        val root = context.projectRoot.path
        if (root.exists()) {
            if (!root.isDirectory()) {
                userReadableError("Project root is not a directory: $root")
            }

            if (root.listDirectoryEntries().any { it.name != "amper" && it.name != "amper.bat" }) {
                userReadableError("Project root is not empty: $root")
            }
         }

        val matchedTemplates = projectTemplates.filterKeys {
            it.contains(template, ignoreCase = true)
        }

        if (matchedTemplates.isEmpty()) {
            userReadableError(
                "No templates were found matching '$template'\n\n" +
                        "Available templates: $availableTemplates"
            )
        }
        if (matchedTemplates.size > 1) {
            userReadableError(
                "Multiple templates (${matchedTemplates.keys.sorted().joinToString("")}) were found matching '$template'\n\n" +
                        "Available templates: $availableTemplates"
            )
        }

        println("Extracting template '${matchedTemplates.keys.single()}' to $root")

        root.createDirectories()

        javaClass.classLoader.getResourceAsStream(matchedTemplates.values.single())!!.use { stream ->
            val zip = ZipInputStream(stream)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name.isEmpty()) {
                    continue
                }
                check(!entry.name.contains("..")) {
                    "'..' is not allowed in zip entry: ${entry.name}"
                }

                val relativeFileName = entry.name.replace('\\', '/').trimStart('/')
                val path = root.resolve(relativeFileName)
                path.parent.createDirectories()
                Files.asByteSink(path.toFile()).writeFrom(zip)
            }
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

        if (publishTasks.isEmpty()) {
            userReadableError("No publish tasks were found for specified module and repository filters")
        }

        println("Tasks that will be executed:\n" +
            publishTasks.map { "  " + it.taskName.name }.sorted().joinToString("\n"))

        runBlocking {
            taskExecutor.runTasksAndReportOnFailure(publishTasks.map { it.taskName })
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

        if (testTasks.isEmpty()) {
            userReadableError("No test tasks were found for specified module and platform filters")
        }

        runBlocking {
            taskExecutor.runTasksAndReportOnFailure(testTasks.map { it.taskName })
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
