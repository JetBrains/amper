package org.jetbrains.deft.proto.core.messages

import java.nio.file.Path

interface ProblemReporter {
    fun reportMessage(message: BuildProblem)
    fun reportError(message: String, file: Path? = null) = reportMessage(BuildProblem(message = message, file = file, level = Level.Error))
    fun reportError(message: String, file: Path, line: Int? = null) = reportMessage(BuildProblem(message = message, level = Level.Error, file = file, line = line))
}

interface ProblemReporterContext {
    val problemReporter: ProblemReporter
}

// TODO: Can be refactored to the reporter chain to avoid inheritance.
// Note: This class is not thread-safe.
// Problems collecting might misbehave when used from multiple threads (e.g. in Gradle).
abstract class CollectingProblemReporter : ProblemReporter {
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
