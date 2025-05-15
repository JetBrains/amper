/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.templates.json

import kotlinx.serialization.Serializable

@Serializable
data class TemplateDescriptor(
    val id: String,
    val resources: List<TemplateResource>,
)

@Serializable
data class TemplateResource(
    val name: String,
    val targetPath: String,
)
