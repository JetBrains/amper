/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.customTaskSchema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaDoc

@SchemaDoc("Defines task type")
enum class CustomTaskType(
    val value: String,
): SchemaEnum {

    @SchemaDoc("An external task executed in a separate JVM process")
    JVM_EXTERNAL_TASK(
        "jvm/external-task",
    );

    override fun toString() = value
    override val schemaValue: String = value
    override val outdated: Boolean = false

    companion object : EnumMap<CustomTaskType, String>(CustomTaskType::values, CustomTaskType::value)
}
