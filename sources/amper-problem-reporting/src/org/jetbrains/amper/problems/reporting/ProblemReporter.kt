/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

import org.jetbrains.annotations.Nls

interface ProblemReporter {
    /**
     * Check if we reported any fatal errors.
     */
    val hasFatal: Boolean

    fun reportMessage(message: BuildProblem)
}

/**
 * A [ProblemReporter] that does nothing.
 */
object NoopProblemReporter : ProblemReporter {
    override val hasFatal get() = false
    override fun reportMessage(message: BuildProblem) = Unit
}

/**
 * A [ProblemReporter] that collects problems so they can be queried later.
 *
 * Note: This class is not thread-safe. Problems collecting might misbehave when used from multiple threads
 * (e.g. in Gradle).
 */
class CollectingProblemReporter : ProblemReporter {
    override val hasFatal get() = problems.any { it.level == Level.Fatal }

    private val myProblems = mutableListOf<BuildProblem>()
    val problems: List<BuildProblem> by ::myProblems

    override fun reportMessage(message: BuildProblem) {
        myProblems.add(message)
    }
}

/**
 * Report all collected problems from the current reporter to the given [other] reporter.
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

private inline fun <T> Collection<T>.forEachEndAware(block: (Boolean, T) -> Unit) =
    forEachIndexed { index, it -> if (index == size - 1) block(true, it) else block(false, it) }
