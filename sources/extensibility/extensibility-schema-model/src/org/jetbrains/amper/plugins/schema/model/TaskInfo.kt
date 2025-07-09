/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskInfo(
    val syntheticType: PluginData.ClassData,
    val jvmFunctionClassName: String,
    val jvmFunctionName: String,
    val inputPropertyNames: List<String> = emptyList(),
    val outputPropertyNames: List<String> = emptyList(),
) {
    init {
        require(inputPropertyNames.intersect(outputPropertyNames).isEmpty())
    }
}