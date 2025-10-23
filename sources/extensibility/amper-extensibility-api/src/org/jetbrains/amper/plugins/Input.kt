/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

/**
 * Marks a [java.nio.file.Path] parameter of a [TaskAction]-annotated method.
 * Signals the framework that the file/directory located by this path is an input to the task action.
 *
 * Dependency wiring will rely on this information: the task, that has this path as an [Output], will be a dependency
 * of the task with this action.
 *
 * Execution avoidance mechanism may rely on this information.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Input(
    /**
     * If true, and if the path is the output of another task, a dependency is automatically inferred
     * between the task declaring this input and the task declaring the *matching* path as output.
     *
     * Input path *matches* the output path means that the input is either exactly the output or any of its subpaths.
     *
     * Note: if disabled,
     * the path's content may still be considered in [execution avoidance][ExecutionAvoidance] computation.
     *
     * A typical case where this should be disabled is when there is a "baseline" file that can be updated and checked.
     * So there is an "update" task that overwrites the baseline with the up-to-date data and a "check" task that
     * checks if the baseline is, in fact, up to date.
     * Now, because the "check" task has the baseline file as an input and the "update" task has it as an output,
     * a dependency would've been inferred between the two. And that is undesired, because then "update" would always
     * run before the "check" and the latter would always succeed.
     * So in this case one would disable the dependency inference on the "check" task's input.
     * Examples of such cases could include linter's baseline, ABI dump file, etc.
     */
    val inferTaskDependency: Boolean = true,
)
