/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun ClassId.toSchemaName() = PluginData.SchemaName(asFqNameString())

private val EXTENSIBILITY_API_PACKAGE = FqName("org.jetbrains.amper")
private val KOTLIN_COLLECTIONS_PACKAGE = FqName("kotlin.collections")

internal val SCHEMA_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Schema"))
internal val TASK_ACTION_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("TaskAction"))
internal val INPUT_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Input"))
internal val OUTPUT_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Output"))

internal val PATH_CLASS = ClassId(FqName("java.nio.file"), Name.identifier("Path"))

internal val EMPTY_LIST = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("emptyList"))
internal val LIST_OF = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("listOf"))
internal val EMPTY_MAP = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("emptyMap"))