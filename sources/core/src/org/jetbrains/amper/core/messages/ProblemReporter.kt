/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import java.nio.file.Path

interface ProblemReporter {
    /**
     * Check if we reported any fatal errors.
     */
    val hasFatal: Boolean

    fun reportMessage(message: BuildProblem)

    /**
     * This method reports a diagnostic that doesn't point to a location in file. It is displayed in IDE as a bar
     * on top of the file. Where possible, reportNodeError should be preferred, because it allows much more precise
     * positioning of the highlighting
     */
    fun reportError(message: String, file: Path? = null) = reportMessage(BuildProblem(message = message, file = file, level = Level.Error))
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

fun renderMessage(problem: BuildProblem): String = buildString {
    problem.file?.let { file ->
        append(file.normalize())
        problem.line?.let { line -> append(":$line") }
        append(": ")
    }
    append(problem.message)
}
