/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.NullableSchemaValue
import org.jetbrains.amper.frontend.api.SchemaValue
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.yaml.psi.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute

/**
 * Try to cast current node to [YAMLScalar].
 * Report if node has different type.
 */
fun YAMLKeyValue.asScalarNode(report: Boolean = true) = value as? YAMLScalar

/**
 * Try to cast current node to [YAMLSequence].
 * Report if node has different type.
 */
// TODO Handle different node type
context(ProblemReporterContext)
fun YAMLPsiElement.asSequenceNode(report: Boolean = true) = this as? YAMLSequence

/**
 * Try to cast current node to [YAMLSequence] and map its contents as list of [YAMLScalar].
 * Report if node has different type.
 */
// TODO Handle different node type
// TODO Handle non scalar entry
context(ProblemReporterContext)
fun YAMLPsiElement.asScalarSequenceNode(report: Boolean = true) : List<YAMLScalar> = (this as? YAMLSequence)
  ?.items
  ?.mapNotNull { it.value as? YAMLScalar } as List<YAMLScalar>

/**
 * Try to cast current node to [MappingNode].
 * Report if node has different type.
 */
// TODO Handle different node type
context(ProblemReporterContext)
fun YAMLPsiElement.asMappingNode(report: Boolean = true) = this as? YAMLMapping

/**
 * Try to find child node by given name and cast it to [YAMLMapping].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetMappingNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.value?.asMappingNode(report)

/**
 * Try to find child node by given name.
 * Report if no node found.
 */
context(ProblemReporterContext)
fun YAMLMapping.tryGetChildNode(name: String, report: Boolean = true): YAMLKeyValue? =
  // TODO Handle non scalar nodes
  keyValues.firstOrNull { it.keyText == name }

/**
 * Try to find child node by given name.
 * Report if no node found.
 */
context(ProblemReporterContext)
fun YAMLSequence.tryGetChildNode(name: String, report: Boolean = true): YAMLSequenceItem? =
  // TODO Handle non scalar nodes
  items.firstOrNull { it.value?.text == name }

/**
 * Try to find child node by given name and cast it to [YAMLPsiElement].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetChildElement(name: String, report: Boolean = true): YAMLKeyValue? =
  keyValues
    .firstOrNull { it?.keyText == name }

/**
 * Try to find child node by given name and cast it to [YAMLScalar].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetScalarNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.asScalarNode(report)

/**
 * Try to find child node by given name and cast it to [YAMLScalar].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLSequence.tryGetScalarNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.value as? YAMLScalar

/**
 * Try to find child node by given name and cast it to [YAMLSequence].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetSequenceNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.value?.asSequenceNode(report)

/**
 * Try to find child node by given name and cast it to [YAMLSequence], and then
 * map its contents as list of [YAMLScalar].
 * Report if no node found or wrong type.
 */
// TODO Handle non scalar entry
context(ProblemReporterContext)
fun YAMLMapping.tryGetScalarSequenceNode(name: String, report: Boolean = true) =
  tryGetSequenceNode(name)?.asScalarSequenceNode()

/**
 * Find all children of this node that are scalar and start with [prefix].
 * Then convert them, skipping null results.
 */
context(ProblemReporterContext)
fun <T> YAMLMapping.convertWithModifiers(
  prefix: String,
  report: Boolean = true,
  convert: YAMLValue.() -> T?
): MutableMap<Modifiers, T> = keyValues
  .filter { it.keyText.startsWith(prefix) }
  .mapNotNull {
    val modifiers = it.extractModifiers()
    // Skip those, that we failed to convert.
    it.value?.convert()?.let {
      modifiers to it
    }
  }
  .toMap()
  .toMutableMap()

/**
 * convert content of this node, treating its keys as [YAMLScalar]s,
 * skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> YAMLMapping.convertScalarKeyedMap(
  report: Boolean = true,
  convert: YAMLValue.(String) -> T?
): Map<String, T> = keyValues.mapNotNull {
  // TODO Report non scalars.
  // Skip non scalar keys.
  val scalarKey = it.keyText
  // Skip those, that we failed to convert.
  val converted = it.value?.convert(scalarKey) ?: return@mapNotNull null
  scalarKey to converted
}
  .toMap()

/**
 * Extract all modifiers that are present within this scalar node.
 */
// TODO Add traces
fun YAMLKeyValue.extractModifiers(): Modifiers =
  keyText.substringAfter("@", "")
    .split("+")
    .filter { it.isNotBlank() }
    .map { TraceableString(it) }
    .toSet()

// TODO Add traces
// TODO Handle non existent
/**
 * convert this scalar node as enum, reporting non-existent values.
 */
context(ProblemReporterContext)
fun <T : Enum<T>, V : YAMLScalar?> V.convertEnum(
  enumIndex: EnumMap<T, String>,
  isFatal: Boolean = false,
  isLong: Boolean = false
): T? = this?.textValue?.let {
  val receivedValue = enumIndex[it]
  if (receivedValue == null) {
    if (isLong) {
      SchemaBundle.reportBundleError(
        this,
        "unknown.property.type.long",
        enumIndex.enumClass.simpleName!!,
        it,
        enumIndex.keys,
        level = if (isFatal) Level.Fatal else Level.Error
      )
    } else {
      SchemaBundle.reportBundleError(
        node = this,
        "unknown.property.type",
        enumIndex.enumClass.simpleName!!,
        it,
        level = if (isFatal) Level.Fatal else Level.Error
      )
    }

  }
  receivedValue
}

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun SchemaValue<String>.invoke(node: YAMLScalar?, onNull: () -> Unit = {}) = this(node?.textValue, onNull).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
context(ProblemReporterContext)
operator fun <T: Enum<T>> SchemaValue<T>.invoke(
  node: YAMLScalar?,
  enumIndex: EnumMap<T, String>,
  isFatal: Boolean = false,
  isLong: Boolean = false
) = this(node?.convertEnum(enumIndex, isFatal = isFatal, isLong = isLong)).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
context(ProblemReporterContext)
operator fun SchemaValue<Boolean>.invoke(node: YAMLScalar?, onNull: () -> Unit = {}) = this(node?.textValue?.toBoolean(), onNull).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
context(ProblemReporterContext)
operator fun SchemaValue<List<String>>.invoke(nodeList: List<YAMLScalar>?, onNull: () -> Unit = {}) = this(nodeList?.values(), onNull).apply { trace = nodeList }

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun NullableSchemaValue<String>.invoke(node: YAMLScalar?) = this(node?.textValue).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
context(ProblemReporterContext)
operator fun NullableSchemaValue<Boolean>.invoke(node: YAMLScalar?) = this(node?.textValue?.toBoolean()).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
context(ProblemReporterContext)
operator fun NullableSchemaValue<List<String>>.invoke(nodeList: List<YAMLScalar>?) = this(nodeList?.values()).apply { trace = nodeList }

/**
 * Map collection of scalar nodes to their string values.
 */
fun Collection<YAMLScalar>.values() = map { it.textValue }

context(ConvertCtx)
fun String.asAbsolutePath(): Path =
  this
    .replace("/", File.separator)
    .let {
      basePath
        .resolve(it)
        .absolute()
        .normalize()
    }

/**
 * Same as [String.asAbsolutePath], but accepts [YAMLScalar].
 */
context(ConvertCtx)
fun YAMLScalar.asAbsolutePath(): Path = textValue.asAbsolutePath()


