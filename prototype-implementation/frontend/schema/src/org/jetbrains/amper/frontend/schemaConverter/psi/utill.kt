/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.api.NullableSchemaValue
import org.jetbrains.amper.frontend.api.SchemaValue
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schemaConverter.asAbsolutePath
import org.jetbrains.yaml.psi.*
import org.yaml.snakeyaml.nodes.*
import java.nio.file.Path

/**
 * Try to cast current node to [ScalarNode].
 * Report if node has different type.
 */
fun YAMLKeyValue.asScalarNode(report: Boolean = true) = value as? YAMLScalar

/**
 * Try to cast current node to [SequenceNode].
 * Report if node has different type.
 */
// TODO Handle different node type
context(ProblemReporterContext)
fun YAMLPsiElement.asSequenceNode(report: Boolean = true) = this as? YAMLSequence

/**
 * Try to cast current node to [SequenceNode] and map its contents as list of [ScalarNode].
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
 * Try to find child node by given name and cast it to [ScalarNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetScalarNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.asScalarNode(report)

/**
 * Try to find child node by given name and cast it to [ScalarNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLSequence.tryGetScalarNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.value as? YAMLScalar

/**
 * Try to find child node by given name and cast it to [SequenceNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun YAMLMapping.tryGetSequenceNode(name: String, report: Boolean = true) =
  tryGetChildNode(name)?.value?.asSequenceNode(report)

/**
 * Try to find child node by given name and cast it to [SequenceNode], and then
 * map its contents as list of [ScalarNode].
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
) = keyValues
  .filter { it.keyText.startsWith(prefix) }
  .mapNotNull {
    val modifiers = it.extractModifiers()
    // Skip those, that we failed to convert.
    it.value?.convert()?.let {
      modifiers to it
    }
  }
  .toMap()

/**
 * convert content of this node, treating its keys as [ScalarNode]s,
 * skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> YAMLMapping.convertScalarKeyedMap(
  report: Boolean = true,
  convert: YAMLValue.() -> T?
) = keyValues.mapNotNull {
  // TODO Report non scalars.
  // Skip non scalar keys.
  val scalarKey = it.keyText
  // Skip those, that we failed to convert.
  val convertd = it.value?.convert() ?: return@mapNotNull null
  scalarKey to convertd
}
  .toMap()

/**
 * Extract all modifiers that are present within this scalar node.
 */
// TODO Add traces
fun YAMLKeyValue.extractModifiers(): Modifiers =
  keyText.
  substringAfter("@", "")
  .split("+")
  .map { TraceableString(it) }
  .toSet()

// TODO Add traces
// TODO Handle non existent
/**
 * convert this scalar node as enum, reporting non-existent values.
 */
fun <T : Enum<T>, V : YAMLScalar?> V.convertEnum(enumIndex: EnumMap<T, String>, report: Boolean = true) =
  this?.textValue?.let { enumIndex.getOrElse(it) { error("No such enum value: $it") } } ?: error("No node value")

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun SchemaValue<String>.invoke(node: YAMLScalar?) = this(node?.textValue).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun NullableSchemaValue<String>.invoke(node: YAMLScalar?) = this(node?.textValue).apply { trace = node }

/**
 * Map collection of scalar nodes to their string values.
 */
fun Collection<YAMLScalar>.values() = map { it.textValue }

/**
 * Same as [String.asAbsolutePath], but accepts [ScalarNode].
 */
fun YAMLScalar.asAbsolutePath(): Path = textValue.asAbsolutePath()


