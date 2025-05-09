/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The context of a task graph execution, available to task actions when the tasks are actually executing.
 */
interface TaskGraphExecutionContext {

    val executionId: String

    /**
     * Registers a piece of code that will run once all tasks have finished running.
     * This allows to clean up resources that are shared between multiple tasks.
     *
     * This is preferable to JVM shutdown hooks because it guarantees that we can still access
     * telemetry and logging (which are themselves shut down in JVM shutdown hooks).
     */
    suspend fun addPostGraphExecutionHook(block: suspend () -> Unit)
}

internal class DefaultTaskGraphExecutionContext : TaskGraphExecutionContext {

    override val executionId: String = UUID.randomUUID().toString()

    private val mutex = Mutex()
    private val postGraphExecutionHooks = mutableListOf<suspend () -> Unit>()
    private var cleanupStarted = false

    /**
     * Registers a piece of code that will run once all tasks have finished running.
     * This allows to clean up resources that are shared between multiple tasks.
     */
    override suspend fun addPostGraphExecutionHook(block: suspend () -> Unit) {
        mutex.withLock {
            if (cleanupStarted) {
                error("Post-graph-execution hooks must not be registered from within a post-graph-execution hook")
            }
            postGraphExecutionHooks.add(block)
        }
    }

    /**
     * Runs all registered post-graph-execution hooks (this should not be called by tasks!).
     */
    suspend fun runPostGraphExecutionHooks() {
        mutex.withLock {
            if (cleanupStarted) {
                error("runPostGraphExecutionHooks() must not be called from within a post-graph-execution hook")
            }
            cleanupStarted = true
            postGraphExecutionHooks.reversed().forEach {
                it.invoke()
            }
        }
    }
}
