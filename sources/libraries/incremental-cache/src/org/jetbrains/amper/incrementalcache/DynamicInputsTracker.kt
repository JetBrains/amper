/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.incrementalcache

import java.nio.file.Path
import kotlin.io.path.exists

/**
 * An instance of this tracker is passed to the computation block called inside [IncrementalCache.execute].
 * It is supposed that all environment parameters that affect cache calculation are taken from this
 * tracker. After the computation block is finished, requested environment parameters and its values
 * requested by computation block are recorded and persisted on disk as [State.dynamicInputs].
 * The next time the computation is run, the new state of these parameters is compared to the recorded state,
 * and the computation is re-run if anything is changed.
 */
class DynamicInputsTracker {
    internal val systemProperties = mutableMapOf<String, String?>()
    internal val environmentVariables = mutableMapOf<String, String?>()
    internal val pathsExistence = mutableMapOf<Path, Boolean>()

    fun readSystemProperty(name: String): String? = System.getProperty(name)
        .also { systemProperties[name] = it }

    fun readEnv(name: String): String? = System.getenv(name)
        .also { environmentVariables[name] = it }

    fun checkPathExistence(path: Path): Boolean = path.exists()
        .also { pathsExistence[path] = it }
}
