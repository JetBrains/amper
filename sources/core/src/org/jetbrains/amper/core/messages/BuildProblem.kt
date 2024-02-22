/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import java.nio.file.Path

typealias BuildProblemId = String

enum class Level {
    /**
     * Cannot process build or import further.
     */
    Fatal,

    /**
     * Can partially finish import or build. Overall build cannot be finished.
     */
    Error,

    /**
     * Can finish import and build.
     */
    Warning,
}

/**
 * Designates the place where the cause of the problem is located.
 */
sealed interface BuildProblemSource

/**
 * Use only when there is no way to pinpoint the cause of the problem inside the Amper files.
 */
data object GlobalBuildProblemSource : BuildProblemSource

interface FileLocatedBuildProblemSource : BuildProblemSource {
    /**
     * Path to the file containing a problem.
     */
    val file: Path

    /**
     * Range of problematic code expressed in terms of lines and columns.
     * Can be used by clients to render the links to the exact location in the file or display an erroneous part of the
     * code.
     */
    val range: LineAndColumnRange?

    /**
     * Range of problematic code expressed in terms of character offsets inside the file.
     * Depending on the client, it might choose [range] or [offsetRange] for displaying an error.
     * The choice depends on what primitives does the client operate with.
     */
    val offsetRange: IntRange?
}

data class BuildProblem(
    val buildProblemId: BuildProblemId,
    val source: BuildProblemSource,
    val message: String,
    val level: Level,
)

data class LineAndColumn(val line: Int, val column: Int, val lineContent: String?) {
    companion object {
        val NONE: LineAndColumn = LineAndColumn(-1, -1, null)
    }
}

/**
 * This range should be interpreted as all the symbols between [start] and [end] inclusive.
 * All the intermediate lines between [start] and [end] are included entirely.
 */
data class LineAndColumnRange(val start: LineAndColumn, val end: LineAndColumn)
