/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

import org.jetbrains.amper.engine.Task

/**
 * A task that supports auto-wiring its dependencies via the artifacts API.
 *
 * @see Artifact
 * @see org.jetbrains.amper.tasks.artifacts.ArtifactTaskBase
 * @see org.jetbrains.amper.tasks.artifacts.PureArtifactTaskBase
 */
interface ArtifactTask : Task {
    /**
     * Declared specs for the input artifacts.
     */
    val consumes: List<ArtifactSelector<*, *>>

    /**
     * Declared specs for the output artifact(s).
     */
    val produces: List<Artifact>

    /**
     * Called by the engine with the resolved artifacts.
     * This happens immediately after the task graph is built.
     *
     * @param artifacts each nested list corresponds to the element in the [consumes] list
     */
    fun injectConsumes(artifacts: Map<ArtifactSelector<*, *>, List<Artifact>>)
}
