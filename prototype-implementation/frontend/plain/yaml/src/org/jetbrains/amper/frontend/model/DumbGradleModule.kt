/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.model

import org.jetbrains.amper.frontend.*
import java.nio.file.Path

class DumbGradleModule(private val file: Path) : PotatoModule {
    override val userReadableName: String
        get() = file.parent.fileName.toString()
    override val type: ProductType
        get() = ProductType.LIB
    override val source: PotatoModuleSource
        get() = PotatoModuleFileSource(file)
    override val fragments: List<Fragment>
        get() = listOf()
    override val artifacts: List<Artifact>
        get() = listOf()

    override val parts =
        classBasedSet<ModulePart<*>>()
}