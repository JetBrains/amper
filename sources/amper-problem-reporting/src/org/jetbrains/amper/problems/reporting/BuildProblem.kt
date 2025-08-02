/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

import org.jetbrains.annotations.Nls

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
