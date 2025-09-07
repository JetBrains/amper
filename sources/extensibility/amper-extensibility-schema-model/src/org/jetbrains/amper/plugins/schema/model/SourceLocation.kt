/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.Serializable

@Serializable
data class SourceLocation(
    val path: PathAsString,
    val textRange: @Serializable(with = RangeSerializer::class) IntRange,
)