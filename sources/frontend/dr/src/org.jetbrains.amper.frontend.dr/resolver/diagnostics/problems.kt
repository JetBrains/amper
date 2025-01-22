/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.diagnostics

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrDiagnosticsRegistrar.reporters
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.BasicDrDiagnostics
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.OverriddenDirectModuleDependencies

internal object DrDiagnosticsRegistrar {
    val reporters = listOf(
        BasicDrDiagnostics(),
        OverriddenDirectModuleDependencies()
    )
}

interface DrDiagnosticsReporter {
    /**
     * The maximal level at which diagnostics are reported by this reporter.
     * If requested level is higher than the level of the diagnostics reporter, then it is skipped.
     */
    val level: Level

    fun reportBuildProblemsForNode(node: DependencyNode, problemReporter: ProblemReporter, level: Level)
}

/**
 * Traverse the entire dependencies graph collecting build problems for every node with the help of predefined
 * list of diagnostics reporters
 */
fun collectBuildProblems(graph: DependencyNode, problemReporter: NoOpCollectingProblemReporter, level: Level) =
    collectBuildProblems(graph, problemReporter, level, reporters)

/**
 * Traverse the entire dependencies graph collecting build problems for every node.
 */
fun collectBuildProblems(graph: DependencyNode, problemReporter: NoOpCollectingProblemReporter, level: Level, diagnosticReporters: List<DrDiagnosticsReporter>){
    for (node in graph.distinctBfsSequence()) {
        //if (node is MavenDependencyNode) {
        //  node.dependency
        //    .files()
        //    .mapNotNull { it.getPath() }
        //    .filterNot { it.name.endsWith("-sources.jar") || it.name.endsWith("-javadoc.jar") }
        //    .forEach { file ->
        //      // todo (AB) : do not throw from import
        //      if (!file.exists()) {
        //        LOG.warn ( "File '$file' was returned from dependency resolution, but is missing on disk" )
        //      }
        //    }
        //}
        diagnosticReporters.forEach {
            if (it.level >= level) {
                it.reportBuildProblemsForNode(node, problemReporter, level)
            }
        }
    }

//    problemReporter.getProblems()
//        .filter { it.source is PsiBuildProblemSource }
//        .distinctBy { (it.source as PsiBuildProblemSource).psiElement to it.message }
//        .forEach { buildProblem ->
//            (buildProblem.source as? PsiBuildProblemSource)?.let {
//                val psiFileProblems = readAction {
//                    dependenciesBuildProblems.computeIfAbsent(it.psiElement.containingFile) { mutableSetOf() }
//                }
//                psiFileProblems.add(buildProblem)
//            }
//        }
//
//    AmperDependenciesProblemsHolder.getInstance(project)
//        .updateProjectDependenciesBuildProblems(dependenciesBuildProblems.mapValues { it.value.toList() })
}

internal fun Message.mapSeverityToLevel(): Level = when (severity) {
    Severity.ERROR -> Level.Error
    Severity.WARNING -> Level.Warning
    Severity.INFO -> Level.Redundancy
}

internal fun Level.mapLevelToSeverity(): Severity? = when (this) {
    Level.Fatal -> null                  // DR doesn't report anything that might stop the overall import process
    Level.Error -> Severity.ERROR
    Level.Warning -> Severity.WARNING
    Level.Redundancy -> Severity.INFO
}