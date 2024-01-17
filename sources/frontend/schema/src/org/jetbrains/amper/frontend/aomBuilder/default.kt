/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.frontend.classBasedSet
import org.jetbrains.amper.frontend.schema.Module
import java.nio.file.Path

data class DefaultModel(override val modules: List<PotatoModule>) : Model

context(ProblemReporterContext)
open class DefaultModule(
    override val userReadableName: String,
    override val type: ProductType,
    override val source: PotatoModuleSource,
    final override val origin: Module,
) : PotatoModule {
    override var fragments = emptyList<DefaultFragment>()
    override var artifacts = emptyList<DefaultArtifact>()
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
)

class DefaultArtifact(
    override val name: String,
    override val fragments: List<LeafFragment>,
    override val isTest: Boolean,
) : Artifact {
    override val platforms = fragments.flatMap { it.platforms }.toSet()
}

class DumbGradleModule(private val file: Path) : PotatoModule {
    override val userReadableName: String
        get() = file.parent.fileName.toString()
    override val type: ProductType
        get() = ProductType.LIB
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(file)
    override val origin: Module
        get() = Module()
    override val fragments: List<Fragment>
        get() = listOf()
    override val artifacts: List<Artifact>
        get() = listOf()

    override val parts =
        classBasedSet<ModulePart<*>>()
}