/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleSource
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.classBasedSet

data class DefaultModel(override val modules: List<PotatoModule>) : Model

class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: PotatoModuleSource,
) : PotatoModule {
    override var fragments = emptyList<DefaultFragment>()
    override var artifacts = emptyList<DefaultArtifact>()
    override var parts = classBasedSet<ModulePart<*>>()
}

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}