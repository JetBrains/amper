/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.frontend.PotatoModuleSource
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path

data class DefaultModel(override val modules: List<PotatoModule>) : Model

context(ProblemReporterContext)
open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: PotatoModuleSource,
    final override val origin: Module,
    override val usedCatalog: VersionCatalog?,
) : PotatoModule {
    override var fragments = emptyList<Fragment>()
    override var artifacts = emptyList<Artifact>()
    override var parts = origin.convertModuleParts()
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
    null
)

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}

class DumbGradleModule(file: Path) : PotatoModule {
    override val userReadableName = file.parent.fileName.toString()
    override val type = ProductType.LIB
    override val source = PotatoModuleFileSource(file)
    override val origin = Module()
    override val fragments = listOf<Fragment>()
    override val artifacts = listOf<Artifact>()
    override val parts = classBasedSet<ModulePart<*>>()
    override val usedCatalog = null
}
