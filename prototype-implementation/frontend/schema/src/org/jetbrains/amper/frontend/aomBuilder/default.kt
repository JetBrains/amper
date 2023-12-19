/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.frontend.PotatoModuleSource
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.schema.Module

data class DefaultModel(override val modules: List<PotatoModule>) : Model

context(ProblemReporterContext)
open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: PotatoModuleSource,
    schemaModule: Module,
) : PotatoModule {
    override var fragments = emptyList<DefaultFragment>()
    override var artifacts = emptyList<DefaultArtifact>()
    override var parts = schemaModule.convertModuleParts()
}

/**
 * Special kind of module, that appears only on
 * internal module resolve failure.
 */
context(ProblemReporterContext)
class NotResolvedModule(
    userReadableName: String,
) : DefaultModule(
    userReadableName,
    ProductType.LIB,
    PotatoModuleProgrammaticSource,
    Module(),
)

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}