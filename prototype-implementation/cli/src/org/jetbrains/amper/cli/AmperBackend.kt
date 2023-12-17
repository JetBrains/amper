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
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.resolve.resolved
import org.jetbrains.amper.tasks.JvmRunTask
import org.jetbrains.amper.tasks.JvmCompileTask
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Paths
import java.util.*
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

        // TODO make it better, fragments should now about parents? or it's alright?
        val fragment2module = resolved.modules
            .flatMap { module -> module.fragments.map { it to module } }
            .toMap(IdentityHashMap())

        fun getTaskOutputPath(taskName: TaskName): TaskOutputRoot {
            val out = context.buildOutputRoot.path.resolve("tasks").resolve(taskName.name.replace(':', '_'))
            return TaskOutputRoot(path = out)
        }

        fun getTaskName(fragment: Fragment, type: JvmTaskType): TaskName {
            val module: PotatoModule = fragment2module[fragment] ?: run {
                error("No module found for fragment: ${fragment.name}")
            }

            val classifier = when (type) {
                JvmTaskType.COMPILE -> "compile"
                JvmTaskType.DEPENDENCIES -> "resolveDependencies"
                JvmTaskType.RUN -> "run"
            }

            // TODO Check how to better name it
            val task = if (fragment.isTest) "${classifier}JvmTest" else "${classifier}Jvm"
            return TaskName.fromHierarchy(listOf(module.userReadableName, task))
        }

        // always process in fixed order, not other requirements yet
        val sortedByPath = resolved.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }

        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

        for (module in sortedByPath) {
            for (fragment in module.fragments) {
                check(fragment is LeafFragment) {
                    "Only leaf fragments are supported, but got '${fragment.javaClass.name}' " +
                            "at module '${module.source}' fragment '${fragment.name}'"
                }

                check(fragment.platform == Platform.JVM) {
                    "Only JVM platform is supported for now, but got '${fragment.platform}' " +
                            "at module '${module.source}' fragment '${fragment.name}'"
                }

                val compileTaskName = getTaskName(fragment, JvmTaskType.COMPILE)
                tasks.registerTask(compileTaskName, JvmCompileTask(module, fragment, context.userCacheRoot, context.projectTempRoot, getTaskOutputPath(compileTaskName), compileTaskName, executeOnChangedInputs))

                val resolveDependenciesTaskName = getTaskName(fragment, JvmTaskType.DEPENDENCIES)
                val task = ResolveExternalDependenciesTask(module, fragment, context.userCacheRoot, resolveDependenciesTaskName, executeOnChangedInputs)
                tasks.registerTask(resolveDependenciesTaskName, task)

                tasks.registerDependency(compileTaskName, dependsOn = resolveDependenciesTaskName)

                // or isDefault?
                if (!fragment.isTest) {
                    // TODO this does not support runtime dependencies
                    //  and dependency graph for it will be different
                    val jvmRunTaskName = getTaskName(fragment, JvmTaskType.RUN)
                    val jvmRunTask = JvmRunTask(jvmRunTaskName, module, fragment, context.userCacheRoot, context.projectRoot)
                    tasks.registerTask(jvmRunTaskName, jvmRunTask)
                    tasks.registerDependency(jvmRunTaskName, compileTaskName)
                }

                //  TODO Maven resolve dependencies task
                // TODO In the future this code should be near compile task

                for (fragmentDependency in fragment.fragmentDependencies) {
                    check(fragmentDependency.type == FragmentDependencyType.FRIEND) {
                        "Unsupported fragment dependency type '${fragmentDependency.type}' " +
                                "at module '${module.source}' fragment '${fragment.name}'"
                    }

                    // TODO Resolve it better
                    val realFragment = module.fragments.single { it.name == fragmentDependency.target.name }

                    val dependencyTaskName = getTaskName(realFragment, JvmTaskType.COMPILE)
                    tasks.registerDependency(compileTaskName, dependencyTaskName)
                }

                for (dependency in fragment.externalDependencies) {
                    when (dependency) {
                        is MavenDependency -> Unit
                        is PotatoModuleDependency -> {
                            // runtime dependencies are not required to be in compile tasks graph

                            if (dependency.compile) {
                                // TODO test with non-resolved dependency on module
                                val resolvedDependencyModule = with(dependency) {
                                    model.module.get()
                                }

                                // TODO not a nice way to get resolved module
                                val realResolvedModule = resolved.modules.single { it.source == resolvedDependencyModule.source }

                                // TODO Dependency on which fragment?
                                // So far, consider all dependencies to be dependencies on non-test target
                                val fragmentToDependOn = realResolvedModule.fragments.filter { !it.isTest }

                                if (fragmentToDependOn.isNotEmpty()) {
                                    "No suitable fragments in module '${realResolvedModule.userReadableName}' to depend on " +
                                            "at module '${module.source}' fragment '${fragment.name}'"
                                }

                                if (fragmentToDependOn.size > 1) {
                                    "Many fragments in module '${realResolvedModule.userReadableName}' suitable to depend on " +
                                            "(" + fragmentToDependOn.joinToString(" ") { it.name } + ")" +
                                            "at module '${module.source}' fragment '${fragment.name}'"
                                }

                                val dependencyTaskName = getTaskName(fragmentToDependOn.single(), JvmTaskType.COMPILE)
                                tasks.registerDependency(compileTaskName, dependencyTaskName)
                            }
                        }
                        else -> error("Unsupported dependency type: '$dependency' "  +
                                "at module '${module.source}' fragment '${fragment.name}'")
                    }
                }
            }
        }

        val taskGraph = tasks.build()
        val taskExecutor = TaskExecutor(taskGraph)

        for (taskName in taskGraph.tasks.keys.sortedBy { it.toString() }) {
            print(taskName)
            if (taskGraph.dependencies.containsKey(taskName)) {
                print(" -> ${taskGraph.dependencies[taskName]!!.joinToString()}")
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

    private enum class JvmTaskType {
        COMPILE,
        DEPENDENCIES,
        RUN,
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
