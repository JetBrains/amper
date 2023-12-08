/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.NullableSchemaValue
import org.jetbrains.amper.frontend.api.SchemaValue
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.Modifiers
import org.yaml.snakeyaml.nodes.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * Try to cast current node to [ScalarNode].
 * Report if node has different type.
 */
fun Node.asScalarNode(report: Boolean = true) = this as? ScalarNode

/**
 * Try to cast current node to [MappingNode].
 * Report if node has different type.
 */
// TODO Handle different node type
context(ProblemReporterContext)
fun Node.asMappingNode(report: Boolean = true) = this as? MappingNode

/**
 * Try to cast current node to [SequenceNode].
 * Report if node has different type.
 */
// TODO Handle different node type
context(ProblemReporterContext)
fun Node.asSequenceNode(report: Boolean = true) = this as? SequenceNode

/**
 * Try to cast current node to [SequenceNode] and map its contents as list of [ScalarNode].
 * Report if node has different type.
 */
// TODO Handle different node type
// TODO Handle non scalar entry
context(ProblemReporterContext)
fun Node.asScalarSequenceNode(report: Boolean = true) = (this as? SequenceNode)
    ?.value
    ?.filterIsInstance<ScalarNode>()

/**
 * Try to find child node by given name.
 * Report if no node found.
 */
context(ProblemReporterContext)
fun MappingNode.tryGetChildNode(name: String, report: Boolean = true) = value
    // TODO Handle non scalar nodes
    .firstOrNull { it.keyNode.asScalarNode()?.value == name }
    ?.valueNode

/**
 * Try to find child node by given name and cast it to [MappingNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun MappingNode.tryGetMappingNode(name: String, report: Boolean = true) =
    tryGetChildNode(name)?.asMappingNode(report)

/**
 * Try to find child node by given name and cast it to [ScalarNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun MappingNode.tryGetScalarNode(name: String, report: Boolean = true) =
    tryGetChildNode(name)?.asScalarNode(report)

/**
 * Try to find child node by given name and cast it to [SequenceNode].
 * Report if no node found or wrong type.
 */
// TODO Report wrong type
context(ProblemReporterContext)
fun MappingNode.tryGetSequenceNode(name: String, report: Boolean = true) =
    tryGetChildNode(name)?.asSequenceNode(report)

/**
 * Try to find child node by given name and cast it to [SequenceNode], and then
 * map its contents as list of [ScalarNode].
 * Report if no node found or wrong type.
 */
// TODO Handle non scalar entry
context(ProblemReporterContext)
fun MappingNode.tryGetScalarSequenceNode(name: String, report: Boolean = true) =
    tryGetSequenceNode(name)
        ?.value
        ?.filterIsInstance<ScalarNode>()

/**
 * Find all children of this node that are scalar and start with [prefix].
 * Then convert them, skipping null results.
 */
context(ProblemReporterContext)
fun <T> MappingNode.convertWithModifiers(
    prefix: String,
    report: Boolean = true,
    convert: Node.() -> T?
) = value
    .filter { it.keyNode.asScalarNode()?.value?.startsWith(prefix) == true }
    .mapNotNull {
        val modifiers = it.keyNode.asScalarNode()!!.extractModifiers()
        // Skip those, that we failed to convert.
        val convertd = it.valueNode.convert() ?: return@mapNotNull null
        modifiers to convertd
    }
    .toMap()

/**
 * convert content of this node, treating its keys as [ScalarNode]s,
 * skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> MappingNode.convertScalarKeyedMap(
    report: Boolean = true,
    convert: Node.() -> T?
) = value.mapNotNull {
        // TODO Report non scalars.
        // Skip non scalar keys.
        val scalarKey = it.keyNode.asScalarNode()?.value ?: return@mapNotNull null
        // Skip those, that we failed to convert.
        val convertd = it.valueNode.convert() ?: return@mapNotNull null
        scalarKey to convertd
    }
    .toMap()

/**
 * Extract all modifiers that are present within this scalar node.
 */
// TODO Add traces
fun ScalarNode.extractModifiers(): Modifiers = value
    .substringAfter("@", "")
    .split("+")
    .map { TraceableString(it) }
    .toSet()

// TODO Add traces
// TODO Handle non existent
/**
 * convert this scalar node as enum, reporting non-existent values.
 */
fun <T : Enum<T>> ScalarNode.convertEnum(values: Collection<T>, report: Boolean = true) =
    values.firstOrNull { it.name.lowercase() == value }

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun SchemaValue<String>.invoke(node: ScalarNode?) = this(node?.value).apply { trace = node }

/**
 * Try to set a value by scalar node, also providing trace.
 */
operator fun NullableSchemaValue<String>.invoke(node: ScalarNode?) = this(node?.value).apply { trace = node }

/**
 * Map collection of scalar nodes to their string values.
 */
fun Collection<ScalarNode>.values() = map { it.value }

/**
 * Try to convert string to absolute path, reporting if
 * file is not-existing.
 */
// TODO Change from error to reporting.
fun String.asAbsolutePath(): Path? =
    Path(this).let { it.takeIf { it.exists() } ?: error("Non existing") }.absolute()

/**
 * Same as [String.asAbsolutePath], but accepts [ScalarNode].
 */
fun ScalarNode.asAbsolutePath(): Path? = value.asAbsolutePath()