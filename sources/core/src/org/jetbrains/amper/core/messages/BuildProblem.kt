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

    /**
     * Can finish import and build.
     * Marks as a redundant declaration ("dead code" in the IDE, INFO level message in console)
     */
    Redundancy,
}

/**
 * Designates the place where the cause of the problem is located.
 */
sealed interface BuildProblemSource

/**
 * Use only when there is no way to pinpoint the cause of the problem inside the Amper files.
 */
@NonIdealDiagnostic
data object GlobalBuildProblemSource : BuildProblemSource

/**
 * Can be used to express the problem with multiple locations (e.g., conflicting declarations).
 */
class MultipleLocationsBuildProblemSource(val sources: List<BuildProblemSource>) : BuildProblemSource {
    constructor(vararg sources: BuildProblemSource): this(sources.toList())

    init {
        require(sources.none { it is MultipleLocationsBuildProblemSource }) { "Only non-nested sources are allowed in a MultipleLocationsBuildProblemSource" }
    }
}

interface FileBuildProblemSource : BuildProblemSource {
    /**
     * Path to the file containing a problem.
     */
    val file: Path
}

interface FileWithRangesBuildProblemSource : FileBuildProblemSource {
    /**
     * Range of problematic code expressed in terms of lines and columns.
     * Can be used by clients to render the links to the exact location in the file or display an erroneous part of the
     * code.
     */
    val range: LineAndColumnRange

    /**
     * Range of problematic code expressed in terms of character offsets inside the file.
     * Depending on the client, it might choose [range] or [offsetRange] for displaying an error.
     * The choice depends on what primitives does the client operate with.
     */
    val offsetRange: IntRange
}

interface BuildProblem {
    val buildProblemId: BuildProblemId
    val source: BuildProblemSource
    val message: String
    val level: Level
}

/**
 * Prefer writing strongly typed build problems
 * (see inheritors of [PsiBuildProblem][org.jetbrains.amper.frontend.messages.PsiBuildProblem] for a reference).
 * They can incorporate additional properties for the IDE to simplify quick-fixes implementation.
 */
data class BuildProblemImpl(
    override val buildProblemId: BuildProblemId,
    override val source: BuildProblemSource,
    override val message: String,
    override val level: Level,
) : BuildProblem

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
