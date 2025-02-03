/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

import java.io.Serializable
import kotlin.reflect.cast

/**
 * A query info to consume artifacts.
 *
 * @see ArtifactTask.consumes
 */
data class ArtifactSelector<T : Artifact, out Q : Quantifier>(
    val type: ArtifactType<T>,
    val predicate: (T) -> Boolean,
    val description: String,
    val quantifier: Q,
) : Serializable {
    fun matches(artifact: Artifact): Boolean {
        return type.clazz.java.isAssignableFrom(artifact.javaClass) && predicate(type.clazz.cast(artifact))
    }

    override fun toString(): String {
        // TODO: Remove variable declaration after Kotlin 2.1
        val quantifierString = when (val q: Quantifier = quantifier) {
            Quantifier.Single -> "single artifact"
            Quantifier.AnyOrNone -> "any/none artifacts"
            Quantifier.Any -> "at least one artifact"
        }
        return "$quantifierString matching type $type, $description"
    }

    companion object
}
