/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.problems.reporting

import org.jetbrains.annotations.Nls

typealias BuildProblemId = String

enum class Level {
    /**
     * A level to inform that something might be improved without impacting the build meaningfully.
     *
     * * The project model can be built successfully
     * * The IDE import can finish successfully
     * * CLI builds are expected to behave correctly
     */
    WeakWarning,
    /**
     * A level to warn about possible misconfiguration.
     *
     * * The project model can be built without breaking invariants, but might be misconfigured
     * * The IDE import can finish successfully
     * * CLI builds will be attempted, but might have issues or unexpected behavior
     */
    Warning,
    /**
     * A level that implies a broken configuration that cannot build.
     *
     * * The project model can be built but may be incomplete or have some values replaced with others so the
     *   invariants can be preserved.
     * * The IDE import can finish (at least partially) to provide best-effort assistance and show broken things.
     * * Any CLI build will fail immediately after parsing the model, before executing any task.
     */
    Error,
    /**
     * A level that implies a very broken configuration that cannot be represented meaningfully with our project model.
     *
     * * The project model cannot be built at all, and thus cannot be imported (at all) in the IDE.
     * * Any CLI build will fail immediately after trying to parse the model, before executing any task.
     */
    // TODO reconsider this level: maybe the failing model construction is enough to convey this. For most of the Fatal
    //   errors in the project, it looks like they could be regular errors and we could just chop off a piece of the
    //   model (like an entire module), without necessarily failing the entire import.
    Fatal,
    ;

    fun atLeastAsSevereAs(other: Level): Boolean = this >= other
}

/**
 * A type of problem. It can be used to present the problem in special ways to the user.
 */
enum class BuildProblemType {
    /**
     * A regular problem without special meaning.
     * It is generally highlighted according to its severity level.
     */
    Generic,
    /**
     * When different parts of the configuration are inconsistent.
     * For example, when some settings are customized for a plugin but the plugin is not enabled.
     */
    InconsistentConfiguration,
    /**
     * For things that should no longer be used, like deprecated elements.
     */
    ObsoleteDeclaration,
    /**
     * For unused or redundant pieces of configuration that have no effect and can be removed.
     */
    RedundantDeclaration,
    /**
     * For values that don't match the expected type.
     */
    TypeMismatch,
    /**
     * For references to something that can't be found.
     */
    UnresolvedReference,
    /**
     * For unknown keys or property names in places where we statically know about the set of allowed names.
     */
    UnknownSymbol;
}

interface BuildProblem {
    val buildProblemId: BuildProblemId

    /**
     * The part of the configuration that is causing this problem.
     * This is used to provide a link in the CLI, or to highlight specific parts of the config in the IDE's editor.
     */
    val source: BuildProblemSource

    /**
     * The message to display to the user.
     */
    val message: @Nls String

    /**
     * Indicates the type of consequences to expect from this build problem.
     * This is used to determine whether CLI builds can be attempted, for example.
     */
    val level: Level

    /**
     * The type of problem this is. It can be used to present the problem in special ways to the user, for example
     * with special highlighting in the IDE editor.
     */
    val type: BuildProblemType
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
    override val type: BuildProblemType,
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
