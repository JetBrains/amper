/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

/**
 * Execution avoidance strategy for [TaskAction]s.
 */
enum class ExecutionAvoidance {
    /**
     * Computes if the task action should be re-run based on the action's arguments and file inputs/outputs state.
     * If the action has no declared outputs, then it always re-runs.
     *
     * @see Input
     * @see Output
     */
    Automatic,

    /**
     * Explicitly states that the task action must always be re-run regardless of input/output state.
     */
    Disabled,
}
