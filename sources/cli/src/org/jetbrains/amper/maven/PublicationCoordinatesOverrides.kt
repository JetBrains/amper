/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.MavenCoordinates

@Serializable
class PublicationCoordinatesOverrides(
    private val overrides: List<PublicationCoordinatesOverride> = emptyList(),
) {
    // maps with non-primitive keys cannot be serialized as JSON as-is
    @Transient
    private val overridesMap = overrides.associate { it.originalCoordinates to it.variantCoordinates }

    /**
     * Creates a new [PublicationCoordinatesOverrides] instance containing all overrides from this and [other].
     */
    operator fun plus(other: PublicationCoordinatesOverrides) =
        PublicationCoordinatesOverrides(overrides = overrides + other.overrides)

    fun actualCoordinatesFor(coords: MavenCoordinates): MavenCoordinates = overridesMap[coords] ?: coords
}

@Serializable
class PublicationCoordinatesOverride(
    val originalCoordinates: MavenCoordinates,
    val variantCoordinates: MavenCoordinates,
)

/**
 * Merges these [PublicationCoordinatesOverrides] into a single instance.
 */
fun Iterable<PublicationCoordinatesOverrides>.merge() =
    fold(PublicationCoordinatesOverrides(), PublicationCoordinatesOverrides::plus)
