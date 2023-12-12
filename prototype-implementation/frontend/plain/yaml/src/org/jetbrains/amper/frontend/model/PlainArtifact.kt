/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.model

import org.jetbrains.amper.frontend.*

context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
internal open class PlainArtifact(
    private val artifactBuilder: ArtifactBuilder,
    override val isTest: Boolean,
) : Artifact {
    override val name: String
        get() = artifactBuilder.name
    override val fragments: List<LeafFragment>
        get() = artifactBuilder.fragments.immutableLeafFragments
    override val platforms: Set<Platform>
        get() = artifactBuilder.platforms
}