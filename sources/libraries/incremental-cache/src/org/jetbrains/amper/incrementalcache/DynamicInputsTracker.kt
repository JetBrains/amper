/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.amper.incrementalcache.IncrementalCache.ExecutionResult
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.exists

/**
 * Provides environment parameters such as system properties, environment variables, and path existence checks.
 * Actual implementation of this interface that should be used is returned by [getDynamicInputs].
 *
 * Inside execution of an incremental cache entry,
 * the instance returned by the function [getDynamicInputs] does track access to environments parameters
 * and includes those in the input state of the cache entry.
 * If any parameter changes, the cache entry is recalculated on later access.
 */
interface DynamicInputs {
    fun readSystemProperty(name: String): String? = System.getProperty(name)
    fun readEnv(name: String): String? = System.getenv(name)
    fun checkPathExistence(path: Path): Boolean = path.exists()
}

internal val dynamicInputsWithoutTracking = object : DynamicInputs {}

suspend fun getDynamicInputs(): DynamicInputs =
    DynamicInputsTracker.getCurrentTracker() ?: dynamicInputsWithoutTracking

/**
 * An instance of this tracker is passed to the computation block called inside [IncrementalCache.execute].
 * It is supposed that all environment parameters that affect cache calculation are taken from this
 * tracker. After the computation block is finished, requested environment parameters and its values
 * requested by computation block are recorded and persisted on disk as [State.dynamicInputs].
 * The next time the computation is run, the new state of these parameters is compared to the recorded state,
 * and the computation is re-run if anything is changed.
 */
internal class DynamicInputsTracker: DynamicInputs {
    internal val systemProperties = mutableMapOf<String, String?>()
    internal val environmentVariables = mutableMapOf<String, String?>()
    internal val pathsExistence = mutableMapOf<Path, Boolean>()

    override fun readSystemProperty(name: String): String? = System.getProperty(name)
        .also { systemProperties[name] = it }

    override fun readEnv(name: String): String? = System.getenv(name)
        .also { environmentVariables[name] = it }

    override fun checkPathExistence(path: Path): Boolean = path.exists()
        .also { pathsExistence[path] = it }

    companion object {
        internal suspend fun getCurrentTracker(): DynamicInputsTracker? =
            currentCoroutineContext()[DynamicInputsTrackerKey]?.dynamicInputsTracker

        private object DynamicInputsTrackerKey: CoroutineContext.Key<DynamicInputsTrackerContextElement>

        private class DynamicInputsTrackerContextElement(
            var dynamicInputsTracker: DynamicInputsTracker
        ) : CoroutineContext.Key<DynamicInputsTrackerContextElement>, CoroutineContext.Element {
            override val key: CoroutineContext.Key<*>
                get() = DynamicInputsTrackerKey

            override fun toString(): String = "DynamicInputsTrackerContextElement"
        }

        internal suspend fun withDynamicInputsTracker(
            tracker: DynamicInputsTracker,
            block: suspend () -> ExecutionResult,
        ): ExecutionResult = withContext(DynamicInputsTrackerContextElement(tracker)) {
            block()
        }
    }
}