/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend


/**
 * Companion object, that adds convenient `invoke` builder function.
 */
abstract class BuilderCompanion<PartBuilderT : Any>(
    private val ctor: () -> PartBuilderT
) {
    operator fun invoke(block: PartBuilderT.() -> Unit) = ctor().apply(block)
}