/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

import org.jetbrains.amper.core.UsedInIdePlugin
import java.nio.file.Path

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

/**
 * Source, pointing to the whole Amper file.
 *
 * N.B. Use only when there is no way to pinpoint the cause of the problem inside the Amper files.
 */
@NonIdealDiagnostic
class WholeFileBuildProblemSource(override val file: Path) : FileBuildProblemSource

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
