/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import java.nio.file.Path

internal data class ResourceItem(
    val type: ResourceType,
    val qualifiers: List<String>,
    val name: String,
    val path: Path,
    val offset: Long = -1,
    val size: Long = -1,
)
