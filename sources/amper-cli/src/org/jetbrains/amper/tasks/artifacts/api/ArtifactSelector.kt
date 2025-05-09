/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts.api

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
) {
    fun matches(artifact: Artifact): Boolean {
        return type.clazz.java.isAssignableFrom(artifact.javaClass) && predicate(type.clazz.cast(artifact))
    }

    override fun toString(): String {
        // TODO: Remove cast after Kotlin 2.1
        @Suppress("USELESS_CAST")
        val quantifierString = when (quantifier as Quantifier) {
            Quantifier.Single -> "single artifact"
            Quantifier.AnyOrNone -> "any/none artifacts"
            Quantifier.AtLeastOne -> "at least one artifact"
        }
        return "$quantifierString matching type $type, $description"
    }
}
