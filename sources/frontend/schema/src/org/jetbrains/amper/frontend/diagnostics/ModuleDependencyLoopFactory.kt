/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.NotResolvedModule
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.graphs.depthFirstDetectLoops
import org.jetbrains.annotations.Nls

class ModuleDependencyLoopProblem(
    val loop: List<Pair<AmperModule, LocalModuleDependency>>,
) : BuildProblem {
    override val buildProblemId get() = diagnosticId
    override val message: @Nls String = SchemaBundle.message(
        "dependencies.modules.loop", loop.joinToString(
            separator = " -> ",
            transform = { it.first.userReadableName },
        )
    )
    override val source: MultipleLocationsBuildProblemSource = MultipleLocationsBuildProblemSource(
        // restore the loop edges using the nodes
        sources = loop.dropLast(1).mapNotNull { it.second.extractPsiElementOrNull() }.map(::PsiBuildProblemSource),
        groupingMessage = SchemaBundle.message("dependencies.modules.loop.grouping"),
    )
    override val level get() = Level.Error

    companion object {
        val diagnosticId: BuildProblemId = "module.dependency.loop"
    }
}

class ModuleDependencySelfProblem(
    val selfDependent: AmperModule,
    val selfDependency: LocalModuleDependency,
) : BuildProblem {
    override val buildProblemId get() = diagnosticId
    override val source: BuildProblemSource = selfDependency.extractPsiElementOrNull()?.let {
        PsiBuildProblemSource(psiElement = it)
    } ?: @OptIn(NonIdealDiagnostic::class) GlobalBuildProblemSource
    override val message = SchemaBundle.message("dependencies.modules.self", selfDependent.userReadableName)
    override val level get() = Level.Error

    companion object {
        val diagnosticId: BuildProblemId = "module.dependency.self"
    }
}

object ModuleDependencyLoopFactory : AomModelDiagnosticFactory {

    override fun analyze(model: Model, problemReporter: ProblemReporter) {
        val graph: Map<AmperModule, List<LocalModuleDependency>> = model.modules.associateWith { node ->
            node.fragments
                .asSequence()
                .sortedByDescending { it.platforms.size }  // more common - first
                // NOTE: test fragments can't introduce the loop, because nobody can depend on them;
                //  if that changes, one must take test fragments into account here as well.
                .filterNot { it.isTest }
                .flatMap { it.externalDependencies }
                .filterIsInstance<LocalModuleDependency>()
                .filter { it.module !is NotResolvedModule }
                // NOTE: We take a single dependency that references a particular module;
                // this may well skip some similar edges. This doesn't impact the overall correctness but may hide
                // other looping edges.
                .distinctBy { it.module }
                .toList()
        }

        fun AmperModule.edges() = checkNotNull(graph[this]) { "Unknown node: $this" }

        val loops = depthFirstDetectLoops(
            roots = graph.keys,
            adjacent = { node -> node.edges().map { it.module } },
        )

        loops.forEach { loop ->
            val problem = if (loop.size == 1) {
                val selfDependent = loop.single()
                ModuleDependencySelfProblem(
                    selfDependent = selfDependent,
                    selfDependency = selfDependent.edges().first { it.module == selfDependent },
                )
            } else {
                val loopWithEdges = loop.plus(loop.first()).windowed(2).map { (from: AmperModule, to: AmperModule) ->
                    from to from.edges().first { edge: LocalModuleDependency -> edge.module == to }
                }.let { it + it.first() }
                ModuleDependencyLoopProblem(
                    loop = loopWithEdges,
                )
            }
            problemReporter.reportMessage(problem)
        }
    }
}
