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

interface BuildProblemSource {
    val file: Path?
    val range: LineAndColumnRange?
    val offsetRange: IntRange?
}

/**
 * Prefer using [org.jetbrains.amper.frontend.messages.PsiBuildProblemSource], which is more tooling-friendly.
 */
data class SimpleProblemSource(
    override val file: Path?,
    override val range: LineAndColumnRange? = null,
    override val offsetRange: IntRange? = null
): BuildProblemSource

data class BuildProblem(
    val buildProblemId: BuildProblemId,
    val message: String,
    val level: Level,
    val source: BuildProblemSource? = null,
)

data class LineAndColumn(val line: Int, val column: Int, val lineContent: String?) {
    companion object {
        val NONE: LineAndColumn = LineAndColumn(-1, -1, null)
    }
}

data class LineAndColumnRange(val start: LineAndColumn, val end: LineAndColumn)
