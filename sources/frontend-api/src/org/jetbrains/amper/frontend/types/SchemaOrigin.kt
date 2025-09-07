/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import java.nio.file.Path

/**
 * [org.jetbrains.amper.frontend.api.Trace] analog for the schema type-system.
 */
sealed interface SchemaOrigin {
    /**
     * Entity comes from the local plugin sources.
     */
    data class LocalPlugin(
        val sourceFile: Path,
        val textRange: IntRange,
    ) : SchemaOrigin

    /**
     * Entity comes from the builtin schema.
     */
    data object Builtin : SchemaOrigin
}