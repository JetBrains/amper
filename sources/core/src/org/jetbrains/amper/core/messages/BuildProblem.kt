/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.annotations.Nls
import java.nio.file.Path

typealias BuildProblemId = String

enum class Level {
    /**
     * Can finish import and build.
     * Marks as a redundant declaration ("dead code" in the IDE, INFO level message in console)
     */
    Redundancy,
    /**
     * Can finish import and build.
     */
    Warning,
    /**
     * Can partially finish import or build. Overall build cannot be finished.
     */
    Error,
    /**
     * Cannot process the build or import further.
     */
    Fatal,
    ;

    fun atLeastAsSevereAs(other: Level): Boolean = this >= other
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
 *
 * @param sources individual file-related problem sources
 * @param groupingMessage a message to be displayed before listing the list of sources,
 *   e.g. `"See here:"` or `"Encountered in:"`
 */
class MultipleLocationsBuildProblemSource(
    val sources: List<FileBuildProblemSource>,
    val groupingMessage: String,
) : BuildProblemSource {
    @UsedInIdePlugin

    constructor(
        vararg sources: FileBuildProblemSource,
        groupingMessage: String,
    ): this(sources.toList(), groupingMessage)
}

interface FileBuildProblemSource : BuildProblemSource {
    /**
     * Path to the file containing a problem.
     */
    val file: Path
}

class DefaultFileBuildProblemSource(override val file: Path) : FileBuildProblemSource

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
    val message: @Nls String
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
    override val message: @Nls String,
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
