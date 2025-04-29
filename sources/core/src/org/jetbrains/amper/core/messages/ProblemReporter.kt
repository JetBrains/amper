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
}

// TODO: Can be refactored to the reporter chain to avoid inheritance.
// Note: This class is not thread-safe.
// Problems collecting might misbehave when used from multiple threads (e.g. in Gradle).
abstract class CollectingProblemReporter : ProblemReporter {
    override val hasFatal get() = problems.any { it.level == Level.Fatal }

    protected val problems: MutableList<BuildProblem> = mutableListOf()

    protected abstract fun doReportMessage(message: BuildProblem)

    override fun reportMessage(message: BuildProblem) {
        problems.add(message)
        doReportMessage(message)
    }
}

class NoOpCollectingProblemReporter : CollectingProblemReporter() {
    fun getProblems(): Collection<BuildProblem> = problems

    override fun doReportMessage(message: BuildProblem) {
    }
}

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
