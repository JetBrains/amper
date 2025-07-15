/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import org.jetbrains.amper.core.forEachEndAware
import org.jetbrains.annotations.Nls

interface ProblemReporter {
    /**
     * Check if we reported any fatal errors.
     */
    val hasFatal: Boolean

    fun reportMessage(message: BuildProblem)
}

interface ProblemReporterContext {
    val problemReporter: ProblemReporter

    /**
     * See [ProblemReporter.hasFatal]
     */
    val hasFatal: Boolean get() = problemReporter.hasFatal
}

/**
 * Temporary workaround to bridge calls from places that have a [ProblemReporter] to legacy places that expect a
 * [ProblemReporterContext].
 */
fun ProblemReporter.asContext() = object : ProblemReporterContext {
    override val problemReporter = this@asContext
}

// TODO: Can be refactored to the reporter chain to avoid inheritance.
// Note: This class is not thread-safe.
// Problems collecting might misbehave when used from multiple threads (e.g. in Gradle).
open class CollectingProblemReporter : ProblemReporter {
    override val hasFatal get() = problems.any { it.level == Level.Fatal }

    private val myProblems = mutableListOf<BuildProblem>()
    val problems: List<BuildProblem> by ::myProblems

    final override fun reportMessage(message: BuildProblem) {
        myProblems.add(message)
        doReportMessage(message)
    }

    protected open fun doReportMessage(message: BuildProblem) = Unit

    fun getDiagnostics(vararg levels: Level = arrayOf(Level.Error, Level.Fatal)): List<BuildProblem> =
        myProblems.filter { levels.contains(it.level) }
}

/**
 * Problem reporter context that collects problems only without any additional actions.
 */
class CollectingOnlyProblemReporterCtx : ProblemReporterContext {
    override val problemReporter = CollectingProblemReporter()
}

/**
 * Report all collected problems from the current context to `other`.
 */
fun CollectingOnlyProblemReporterCtx.replayProblemsTo(other: ProblemReporterContext) = 
    problemReporter.replayProblemsTo(other.problemReporter)

/**
 * Report all collected problems from the current reporter to `other`.
 */
fun CollectingProblemReporter.replayProblemsTo(other: ProblemReporter) = 
    problems.forEach { other.reportMessage(it) }

@OptIn(NonIdealDiagnostic::class)
fun renderMessage(problem: BuildProblem): @Nls String = buildString {
    fun appendSource(source: FileBuildProblemSource, appendMessage: Boolean = true) {
        append(source.file.normalize())
        if (source is FileWithRangesBuildProblemSource) {
            val start = source.range.start
            append(':').append(start.line).append(':').append(start.column)
        }
        if (appendMessage) {
            append(": ").append(problem.message)
        }
    }

    fun appendSource(source: BuildProblemSource) {
        when (source) {
            is FileBuildProblemSource -> appendSource(source)
            is MultipleLocationsBuildProblemSource -> {
                appendLine(problem.message)
                appendLine("╰─ ${source.groupingMessage}")
                source.sources.forEachEndAware { isLast, it ->
                    append("   ╰─ ")
                    appendSource(it, appendMessage = false)
                    if (!isLast) appendLine()
                }
            }
            GlobalBuildProblemSource -> append(problem.message)
        }
    }

    appendSource(problem.source)
}
