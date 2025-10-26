/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun ClassId.toSchemaName() = PluginData.SchemaName(
    packageName = packageFqName.asString(),
    simpleNames = relativeClassName.pathSegments().map { it.asString() },
)

// Amper Extensibility API:

/**
 * Public Extensibility API root package name.
 */
val EXTENSIBILITY_API_PACKAGE = FqName("org.jetbrains.amper.plugins")

val CONFIGURABLE_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Configurable"))
val INPUT_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Input"))
val INFER_TASK_DEPENDENCY_PARAM = Name.identifier("inferTaskDependency")
val OUTPUT_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Output"))

val PROVIDED_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Provided"))
val SHORTHAND_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("Shorthand"))
val DEP_NOTATION_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("DependencyNotation"))
val PATH_VALUE_ONLY_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("PathValueOnly"))

val ENUM_VALUE_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("EnumValue"))

val TASK_ACTION_ANNOTATION_CLASS = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("TaskAction"))
val TASK_ACTION_EXEC_AVOIDANCE_PARAM = Name.identifier("executionAvoidance")
val EXEC_AVOIDANCE_ENUM = ClassId(EXTENSIBILITY_API_PACKAGE, Name.identifier("ExecutionAvoidance"))
val EXEC_AVOIDANCE_DISABLED = CallableId(EXEC_AVOIDANCE_ENUM, Name.identifier("Disabled"))

// System API used by Amper:

private val KOTLIN_COLLECTIONS_PACKAGE = FqName("kotlin.collections")

internal val PATH_CLASS = ClassId(FqName("java.nio.file"), Name.identifier("Path"))

internal val EMPTY_LIST = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("emptyList"))
internal val LIST_OF = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("listOf"))
internal val EMPTY_MAP = CallableId(KOTLIN_COLLECTIONS_PACKAGE, Name.identifier("emptyMap"))