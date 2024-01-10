/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.fasterxml.jackson.databind.JsonMappingException
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
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.resolve.resolved
import org.jetbrains.amper.tasks.JvmCompileTask
import org.jetbrains.amper.tasks.JvmRunTask
import org.jetbrains.amper.tasks.JvmTestTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Paths
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
                CommonTaskType.RUN -> "run$platformSuffix"
                CommonTaskType.TEST -> "test$platformSuffix"
            }

            return TaskName.fromHierarchy(listOf(module.userReadableName, taskName))
        }

        // always process in fixed order, not other requirements yet
        val sortedByPath = resolved.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }

        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

        for (module in sortedByPath) {
            // TODO dunno how to get it legally
            val modulePlatforms = (module.fragments.flatMap { it.platforms } +
                    module.artifacts.flatMap { it.platforms })
                .filter { it.isLeaf }
                .toSet()
            // TODO remove
            println("distinct module platforms ${module.userReadableName}: ${modulePlatforms.sortedBy { it.name }.joinToString()}")

            // JVM stuff building
            if (modulePlatforms.contains(Platform.JVM)) {
                for (isTest in listOf(false, true)) {
                    val fragments = module.fragments
                        .filter { it.isTest == isTest && it.platforms.contains(Platform.JVM) }
                    if (isTest && fragments.isEmpty()) {
                        // no test code, assume no code generation
                        // other modules could not depend on this module's tests, so it's ok
                        continue
                    }

                    val resolveDependenciesTask = ResolveExternalDependenciesTask(
                        module,
                        context.userCacheRoot,
                        executeOnChangedInputs,
                        isTest = isTest,
                        platform = Platform.JVM,
                        taskName = getTaskName(module, CommonTaskType.DEPENDENCIES, Platform.JVM, isTest = isTest)
                    ).also { tasks.registerTask(it) }

                    val compileTaskName = getTaskName(module, CommonTaskType.COMPILE, Platform.JVM, isTest)
                    tasks.registerTask(
                        JvmCompileTask(
                            module,
                            fragments,
                            context.userCacheRoot,
                            context.projectTempRoot,
                            getTaskOutputPath(compileTaskName),
                            compileTaskName,
                            executeOnChangedInputs,
                        ),
                        dependsOn = listOf(resolveDependenciesTask.taskName),
                    )

                    // TODO this does not support runtime dependencies
                    //  and dependency graph for it will be different
                    if (isTest) {
                        val jvmTestTaskName = getTaskName(module, CommonTaskType.TEST, Platform.JVM, isTest = true)
                        tasks.registerTask(
                            JvmTestTask(
                                userCacheRoot = context.userCacheRoot,
                                taskOutputRoot = getTaskOutputPath(jvmTestTaskName),
                                projectRoot = context.projectRoot,
                                module = module,
                                taskName = jvmTestTaskName,
                            ),
                            dependsOn = listOf(compileTaskName),
                        )
                    } else {
                        if (module.type == ProductType.JVM_APP || module.type == ProductType.LEGACY_APP) {
                            val jvmRunTaskName = getTaskName(module, CommonTaskType.RUN, Platform.JVM, isTest = false)
                            tasks.registerTask(
                                JvmRunTask(
                                    jvmRunTaskName,
                                    module,
                                    context.userCacheRoot,
                                    context.projectRoot,
                                ),
                                dependsOn = listOf(compileTaskName),
                            )
                        }
                    }

                    // TODO In the future this code should be near compile task
                    // TODO What to do with fragment.fragmentDependencies?
                    //  I'm not sure, it's just test -> non-test dependency? Otherwise we build it by platforms
                    if (isTest) {
                        val dependencyTaskName = getTaskName(module, CommonTaskType.COMPILE, Platform.JVM, false)
                        tasks.registerDependency(compileTaskName, dependencyTaskName)
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

                                    val dependencyTaskName = getTaskName(resolvedDependencyModule, CommonTaskType.COMPILE,Platform.JVM, isTest = false)
                                    tasks.registerDependency(compileTaskName, dependencyTaskName)
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

/*
        val objectMapper = jacksonObjectMapper().also {
            it.enable(SerializationFeature.INDENT_OUTPUT)
            val typer = object : ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.NON_FINAL) {
                override fun useForType(t: JavaType): Boolean {
                    return !(t.isCollectionLikeType() || t.isMapLikeType()) && super.useForType(t)
                }
            }
                .init(JsonTypeInfo.Id.NAME, null)
                .inclusion(JsonTypeInfo.As.PROPERTY)
                .typeProperty("\$type")

            it.setDefaultTyping(typer)
//            it.registerModule(SimpleModule().addSerializer(, ))
        }

        println(objectMapper.writeValueAsString(model))
*/

        return if (problemReporter.wereProblemsReported()) 1 else 0
    }

    private enum class CommonTaskType {
        COMPILE,
        DEPENDENCIES,
        RUN,
        TEST,
    }
}

data class TaskName(val name: String) {
    init {
        require(name.isNotBlank())
    }

    companion object {
        fun fromHierarchy(path: List<String>) = TaskName(path.joinToString(":", prefix = ":"))
    }
}

// useful for tinkering/testing
fun main() {
    try {
        val root = Paths.get("/Users/shalupov/work/deft-prototype/examples/modularized")

        AmperBackend.run(
            context = ProjectContext.create(root),
            tasksToRun = listOf(":app:compileJvmTest"),
        )
    } catch (t: JsonMappingException) {
        t.printStackTrace()
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
