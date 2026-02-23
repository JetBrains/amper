/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Misnomers
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.schema.ModuleProduct

/**
 * Internal schema to read fields, which are crucial for contexts generation.
 * Must be fully compatible with [org.jetbrains.amper.frontend.schema.Module].
 */
class MinimalModule : SchemaNode() {
    val product by value<ModuleProduct>()

    val aliases by nullableValue<Map<String, List<TraceableEnum<Platform>>>>()

    @Misnomers("templates")
    val apply by nullableValue<List<TraceablePath>>()
}