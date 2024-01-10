/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.get
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.resolve.resolved
import org.jetbrains.amper.tasks.JvmCompileTask
import org.jetbrains.amper.tasks.JvmRunTask
import org.jetbrains.amper.tasks.JvmTestTask
import org.jetbrains.amper.tasks.NativeCompileTask
import org.jetbrains.amper.tasks.NativeRunTask
import org.jetbrains.amper.tasks.NativeTestTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.Task
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.exists
import kotlin.io.path.pathString

object AmperBackend {
    fun run(context: ProjectContext, tasksToRun: List<String>): Int = with(CliProblemReporterContext) {
        val model = spanBuilder("loading model").setAttribute("root", context.projectRoot.path.pathString).startSpan().use {
            when (val result = ModelInit.getModel(context.projectRoot.path)) {
                is Result.Failure -> throw result.exception
                is Result.Success -> result.value
            }
        }

        val resolved = model.resolved

        fun getTaskOutputPath(taskName: TaskName): TaskOutputRoot {
            val out = context.buildOutputRoot.path.resolve("tasks").resolve(taskName.name.replace(':', '_'))
            return TaskOutputRoot(path = out)
        }

        fun getTaskName(module: PotatoModule, type: CommonTaskType, platform: Platform, isTest: Boolean): TaskName {
            val testSuffix = if (isTest) "Test" else ""
            val platformSuffix = platform.pretty.replaceFirstChar { it.uppercase() }

            val taskName = when (type) {
                CommonTaskType.COMPILE -> "compile$platformSuffix$testSuffix"
                CommonTaskType.DEPENDENCIES -> "resolveDependencies$platformSuffix$testSuffix"
                CommonTaskType.RUN -> {
                    require(!isTest)
                    "run$platformSuffix"
                }
                CommonTaskType.TEST -> {
                    require(isTest)
                    "test$platformSuffix"
                }
            }

            return TaskName.fromHierarchy(listOf(module.userReadableName, taskName))
        }

        // always process in fixed order, not other requirements yet
        val sortedByPath = resolved.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }

        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

        for (module in sortedByPath) {
            val modulePlatforms = module.targetLeafPlatforms
            logger.info("distinct module platforms ${module.userReadableName}: ${modulePlatforms.sortedBy { it.name }.joinToString()}")

            for (platform in modulePlatforms) {
                for (isTest in listOf(false, true)) {
                    val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
                    if (isTest && fragments.all { !it.src.exists() }) {
                        // no test code, assume no code generation
                        // other modules could not depend on this module's tests, so it's ok
                        continue
                    }

                    fun createCompileTask(): Task {
                        val compileTaskName = getTaskName(module, CommonTaskType.COMPILE, platform, isTest = isTest)
                        return when (val top = platform.topmostParentNoCommon) {
                            Platform.JVM -> JvmCompileTask(
                                module = module,
                                fragments = fragments,
                                userCacheRoot = context.userCacheRoot,
                                tempRoot = context.projectTempRoot,
                                taskOutputRoot = getTaskOutputPath(compileTaskName),
                                taskName = compileTaskName,
                                executeOnChangedInputs = executeOnChangedInputs,
                            )

                            Platform.NATIVE -> NativeCompileTask(
                                module = module,
                                platform = platform,
                                userCacheRoot = context.userCacheRoot,
                                taskOutputRoot = getTaskOutputPath(compileTaskName),
                                executeOnChangedInputs = executeOnChangedInputs,
                                taskName = compileTaskName,
                                tempRoot = context.projectTempRoot,
                                isTest = isTest,
                            )

                            else -> error("$top is not supported yet")
                        }.also {
                            tasks.registerTask(it, dependsOn = listOf(getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest = isTest)))
                        }
                    }

                    fun createResolveTask(): Task =
                        ResolveExternalDependenciesTask(
                            module,
                            context.userCacheRoot,
                            executeOnChangedInputs,
                            isTest = isTest,
                            platform = platform,
                            taskName = getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest = isTest)
                        ).also { tasks.registerTask(it) }

                    fun createRunTask(): Task {
                        require(!isTest)
                        require(!module.type.isLibrary())

                        val runTaskName = getTaskName(module, CommonTaskType.RUN, platform, isTest = false)
                        return when (val top = platform.topmostParentNoCommon) {
                            Platform.JVM -> JvmRunTask(
                                module = module,
                                userCacheRoot = context.userCacheRoot,
                                projectRoot = context.projectRoot,
                                taskName = runTaskName,
                            )

                            Platform.NATIVE -> NativeRunTask(
                                module = module,
                                projectRoot = context.projectRoot,
                                taskName = runTaskName,
                            )

                            else -> error("$top is not supported yet")
                        }.also {
                            tasks.registerTask(it, dependsOn = listOf(getTaskName(module, CommonTaskType.COMPILE, platform, isTest = false)))
                        }
                    }

                    fun createTestTask(): Task {
                        require(isTest)

                        val testTaskName = getTaskName(module, CommonTaskType.TEST, platform, isTest = true)
                        return when (val top = platform.topmostParentNoCommon) {
                            Platform.JVM -> JvmTestTask(
                                module = module,
                                userCacheRoot = context.userCacheRoot,
                                projectRoot = context.projectRoot,
                                taskName = testTaskName,
                                taskOutputRoot = getTaskOutputPath(testTaskName),
                            )

                            Platform.NATIVE -> NativeTestTask(
                                module = module,
                                projectRoot = context.projectRoot,
                                taskName = testTaskName,
                            )

                            else -> error("$top is not supported yet")
                        }.also {
                            tasks.registerTask(it, dependsOn = listOf(getTaskName(module, CommonTaskType.COMPILE, platform, isTest = true)))
                        }
                    }

                    createResolveTask()
                    createCompileTask()

                    // TODO this does not support runtime dependencies
                    //  and dependency graph for it will be different
                    if (isTest) {
                        createTestTask()
                    } else {
                        if (!module.type.isLibrary()) {
                            createRunTask()
                        }
                    }

                    // TODO In the future this code should be near compile task
                    // TODO What to do with fragment.fragmentDependencies?
                    //  I'm not sure, it's just test -> non-test dependency? Otherwise we build it by platforms
                    if (isTest) {
                        tasks.registerDependency(
                            taskName = getTaskName(module, CommonTaskType.COMPILE, platform, true),
                            dependsOn = getTaskName(module, CommonTaskType.COMPILE, platform, false)
                        )
                    }

                    for ((fragment, dependency) in fragments.flatMap { fragment -> fragment.externalDependencies.map { fragment to it } }) {
                        when (dependency) {
                            is MavenDependency -> Unit
                            is PotatoModuleDependency -> {
                                // runtime dependencies are not required to be in compile tasks graph
                                if (dependency.compile) {
                                    // TODO test with non-resolved dependency on module
                                    val resolvedDependencyModule = with(dependency) {
                                        model.module.get()
                                    }

                                    tasks.registerDependency(
                                        taskName = getTaskName(module, CommonTaskType.COMPILE, platform, isTest = isTest),
                                        dependsOn = getTaskName(resolvedDependencyModule, CommonTaskType.COMPILE, platform, isTest = false),
                                    )
                                }
                            }
                            else -> error("Unsupported dependency type: '$dependency' "  +
                                    "at module '${module.source}' fragment '${fragment.name}'")
                        }
                    }
                }
            }
        }

        val taskGraph = tasks.build()
        val taskExecutor = TaskExecutor(taskGraph)

        for (taskName in taskGraph.tasks.keys.sortedBy { it.toString() }) {
            print("task ${taskName.name}")
            if (taskGraph.dependencies.containsKey(taskName)) {
                print(" -> ${taskGraph.dependencies[taskName]!!.joinToString { it.name }}")
            }
            println()
        }

        if (tasksToRun.isEmpty()) {
            error("Empty tasks list to run")
        }

        runBlocking(Dispatchers.Default) {
            taskExecutor.run(tasksToRun.map { TaskName(it) })
        }

        return if (problemReporter.wereProblemsReported()) 1 else 0
    }

    private enum class CommonTaskType {
        COMPILE,
        DEPENDENCIES,
        RUN,
        TEST,
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}

data class TaskName(val name: String) {
    init {
        require(name.isNotBlank())
    }

    companion object {
        fun fromHierarchy(path: List<String>) = TaskName(path.joinToString(":", prefix = ":"))
    }
}
