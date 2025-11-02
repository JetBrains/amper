/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.plugins.GeneratedPathKind
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowSourcesKind
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MessageBundle
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path

internal class TaskGraphBuildContext(
    val buildDir: Path,
    val projectRootDir: Path,
)

internal object FrontendTaskGraphBundle : MessageBundle("messages.FrontendTaskGraphBundle")

/**
 * Builds "frontend" task graph.
 *
 * @see diagnoseTaskDependencyLoops
 * @see wireTaskDependencies
 */
context(reporter: ProblemReporter, _: TaskGraphBuildContext)
internal fun buildTaskGraph(
    tasks: List<TaskFromPluginDescription>,
    modules: List<AmperModule>,
): TaskGraph {
    val graphBuilder = TaskGraphBuilder()

    // Simulate a rough model of the builtin module build stages
    for (module in modules) {
        val compilation = TaskGraph.Node.Compilation(module).also { graphBuilder.nodes += it }
        val sourceGeneration = TaskGraph.Node.SourceGeneration(module).also { graphBuilder.nodes += it }
        val resourceGeneration = TaskGraph.Node.ResourceGeneration(module).also { graphBuilder.nodes += it }
        graphBuilder[compilation].apply {
            add(TaskGraph.Edge(sourceGeneration, DefaultTrace) {
                FrontendTaskGraphBundle.message("task.graph.reasons.builtin.compilation.includes.sources")
            })
            add(TaskGraph.Edge(resourceGeneration, DefaultTrace) {
                FrontendTaskGraphBundle.message("task.graph.reasons.builtin.compilation.includes.resources")
            })
        }
    }

    // Establish dependencies between plugin tasks
    for (task in tasks) {
        val taskNode = TaskGraph.Node.PluginTask(task).also { graphBuilder.nodes += it }
        val dependencies = graphBuilder[taskNode]

        dependencies += TaskGraph.Edge(TaskGraph.Node.Compilation(task.codeSource), DefaultTrace) {
            FrontendTaskGraphBundle.message("task.graph.reasons.builtin.compilation.self")
        }

        for (request in task.requestedClasspaths) {
            for (module in request.localDependencies) {
                var trace = request.node.dependencies
                    .first { it is ShadowDependencyLocal && it.modulePath == module.source.moduleDir }.trace
                if (trace.isDefault) { trace = request.node.trace }
                dependencies += TaskGraph.Edge(TaskGraph.Node.Compilation(module), trace) {
                    FrontendTaskGraphBundle.message("task.graph.reasons.classpath.resolution.local")
                }
            }
        }

        for (request in task.requestedCompilationArtifacts) {
            dependencies += TaskGraph.Edge(TaskGraph.Node.Compilation(request.from), request.node.trace) {
                FrontendTaskGraphBundle.message("task.graph.reasons.compilation.result")
            }
        }

        for (request in task.requestedModuleSources) {
            if (request.node.includeGenerated) {
                val generation = when (request.node.kind) {
                    ShadowSourcesKind.KotlinJavaSources -> TaskGraph.Node.SourceGeneration(request.from)
                    ShadowSourcesKind.Resources -> TaskGraph.Node.ResourceGeneration(request.from)
                }
                dependencies += TaskGraph.Edge(generation, request.node::includeGenerated.schemaDelegate.trace) {
                    FrontendTaskGraphBundle.message(
                        when (request.node.kind) {
                            ShadowSourcesKind.KotlinJavaSources -> "task.graph.reasons.generated.sources"
                            ShadowSourcesKind.Resources -> "task.graph.reasons.generated.resources"
                        }
                    )
                }
            }
        }

        for ((path, outputMark) in task.outputs) {
            if (outputMark == null || outputMark.associateWith.isTest) {
                // Skip test sources/resources because they can't yet form a loop
                continue
            }
            // Here we don't take the platform into account
            when (outputMark.kind) {
                GeneratedPathKind.KotlinSources,
                GeneratedPathKind.JavaSources,
                    ->
                    graphBuilder[TaskGraph.Node.SourceGeneration(outputMark.associateWith.module)]
                GeneratedPathKind.JvmResources ->
                    graphBuilder[TaskGraph.Node.ResourceGeneration(outputMark.associateWith.module)]
            } += TaskGraph.Edge(taskNode, outputMark.trace) {
                FrontendTaskGraphBundle.message(
                    "task.graph.reasons.generation.includes.directory",
                    path.value.replaceKnownSuperpaths()
                )
            }
        }

        val taskOutputs = tasks.flatMap { task ->
            task.outputs.map { it.path to task }
        }
        for (inputPath in task.inputs) {
            if (!inputPath.inferTaskDependency)
                continue

            val producedBy = taskOutputs.filter { (path, _) ->
                // TODO: optimize this
                path.value.startsWith(inputPath.path.value) || inputPath.path.value.startsWith(path.value)
            }
            if (producedBy.isEmpty()) {
                if (inputPath.path.value.mustBeProduced()) {
                    reporter.reportBundleError(
                        inputPath.path.asBuildProblemSource(),
                        "plugin.tasks.paths.unproduced.in.build.dir",
                        inputPath.path.value.replaceKnownSuperpaths(),
                        level = Level.Warning,
                    )
                }
                continue
            }
            for ((path, task) in producedBy) {
                dependencies += TaskGraph.Edge(TaskGraph.Node.PluginTask(task), path.trace) {
                    FrontendTaskGraphBundle.message(
                        "task.graph.reasons.input.output.matched",
                        path.value.replaceKnownSuperpaths()
                    )
                }
            }
        }
    }

    return graphBuilder
}

private class TaskGraphBuilder : TaskGraph {
    private val edges = mutableMapOf<TaskGraph.Node, MutableList<TaskGraph.Edge>>()
    override val nodes = mutableListOf<TaskGraph.Node>()

    override fun get(node: TaskGraph.Node): MutableList<TaskGraph.Edge> {
        return edges.getOrPut(node, ::mutableListOf)
    }
}
