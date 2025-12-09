/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.reading.maven.validateAndReportMavenCoordinates
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.frontend.types.withNullability
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.problems.reporting.replayProblemsTo
import org.jetbrains.amper.stdlib.collections.joinToString
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Resolves [ReferenceNode]s and [StringInterpolationNode]s in the tree and returns the substituted result.
 * The returned value may still contain unresolved references,
 * but they all are guaranteed to have been reported as errors.
 */
context(buildCtx: BuildCtx)
internal fun RefinedMappingNode.resolveReferences(): RefinedMappingNode {
    val reporter = CollectingProblemReporter()

    var value: RefinedMappingNode = this
    var resolver: TreeReferencesResolver? = null
    do {
        if (resolver != null && resolver.deferred.isEmpty()) {
            // Nothing more to resolve
            break
        }
        resolver = TreeReferencesResolver(
            reporter = reporter,
            resolveOnly = resolver?.deferred?.let { HashSet(it) }
        )
        value = resolver.visitMap(value)
        if (!resolver.anyResolutionDone) {
            // No more changes made - stop the resolution process
            break
        }
    } while (true)

    reporter.replayProblemsTo(buildCtx.problemReporter)
    if (reporter.problems.any { it.level.atLeastAsSevereAs(Level.Error) }) {
        // We do not diagnose the looped references if there were some other resolution errors.
        return value
    }

    // All the remaining `deferred` references form one or more cycles between each other.
    val remainingReferences = resolver.deferred
    if (remainingReferences.isNotEmpty()) {
        val source = MultipleLocationsBuildProblemSource(
            sources = remainingReferences
                .mapNotNull { it.trace.asBuildProblemSource() as? FileBuildProblemSource }.toList(),
            groupingMessage = SchemaBundle.message("validation.reference.resolution.loops.grouping")
        )
        buildCtx.problemReporter.reportBundleError(source, "validation.reference.resolution.loops")
    }

    return value
}

private class TreeReferencesResolver(
    private val reporter: ProblemReporter,
    private val resolveOnly: Set<RefinedTreeNode>? = null,
) : RefinedTreeTransformer() {
    val deferred = mutableListOf<RefinedTreeNode>()
    var anyResolutionDone = false

    private val currentPath = mutableListOf<RefinedMappingNode>()

    override fun visitMap(node: RefinedMappingNode): RefinedMappingNode {
        currentPath.add(node)
        return super.visitMap(node).also {
            currentPath.removeLast()
        }
    }

    override fun visitReference(node: ReferenceNode): RefinedTreeNode {
        if (resolveOnly != null && node !in resolveOnly) {
            return node
        }

        val resolved = resolve(node, node.referencedPath) ?: return node
        val trace = node.resolvedTrace(resolved)
        val converted = resolved.cast(targetType = node.type, trace = trace) ?: run {
            reporter.reportBundleError(
                node.trace.asBuildProblemSource(), "validation.reference.unexpected.type",
                renderTypeOf(resolved), node.type.render(includeSyntax = false)
            )
            return node
        }

        anyResolutionDone = true
        return converted.copyWithTrace(trace = trace)
    }

    override fun visitStringInterpolation(node: StringInterpolationNode): RefinedTreeNode {
        if (resolveOnly != null && node !in resolveOnly) {
            return node
        }

        val allResolvedValues = mutableListOf<Traceable>()
        val interpolated = node.parts.map { part ->
            when (part) {
                is StringInterpolationNode.Part.Reference -> {
                    val resolved = resolve(node, part.referencePath) ?: return node
                    val converted = resolved.cast(SchemaType.StringType) ?: run {
                        reporter.reportBundleError(
                            node.trace.asBuildProblemSource(), "validation.reference.unexpected.type.interpolation",
                            renderTypeOf(resolved),
                        )
                        return node
                    }
                    allResolvedValues += converted
                    (converted as ScalarNode).value.toString()
                }
                is StringInterpolationNode.Part.Text -> part.text
            }
        }.joinToString("")

        val trace = TransformedValueTrace(
            description = "string interpolation: ${node.parts.joinToString("") {
                when(it) {
                    is StringInterpolationNode.Part.Reference -> $$"${$${it.referencePath.joinToString(".")}}"
                    is StringInterpolationNode.Part.Text -> it.text
                }
            }}",
            definitionTrace = node.trace,
            // TODO: Support multi-source traces
            // first() is safe because of StringInterpolationValue contract.
            sourceValue = allResolvedValues.first(),
        )
        val typedInterpolated = when (val type = node.type) {
            is SchemaType.PathType -> try {
                Path(interpolated).normalize().wrapTraceable(type, trace)
            } catch (e: InvalidPathException) {
                reporter.reportBundleError(trace.asBuildProblemSource(), "validation.types.invalid.path", e.message)
                return node
            }
            is SchemaType.StringType -> {
                when (type.semantics) {
                    SchemaType.StringType.Semantics.MavenCoordinates -> context(reporter) {
                        validateAndReportMavenCoordinates(
                            origin = checkNotNull(trace.extractPsiElementOrNull()) {
                                "String interpolation is expected to always originate from the PSI"
                            },
                            coordinates = interpolated
                        )
                    }
                    SchemaType.StringType.Semantics.JvmMainClass,
                    SchemaType.StringType.Semantics.PluginSettingsClass,
                    null -> {}
                }
                interpolated.wrapTraceable(type, trace)
            }
        }

        anyResolutionDone = true
        return ScalarNode(typedInterpolated, node.type, trace, node.contexts)
    }

    private fun resolve(
        origin: RefinedTreeNode,
        referencePath: List<String>,
    ): RefinedTreeNode? {
        val firstElement = referencePath.first()
        val reversedPath = currentPath.asReversed()

        val resolutionRoot = reversedPath.find { it[firstElement] != null } ?: run {
            reporter.reportBundleError(
                origin.trace.asBuildProblemSource(), "validation.reference.resolution.root.not.found",
                arguments = arrayOf(firstElement),
            )
            return null
        }

        val resolved = referencePath.foldIndexed(resolutionRoot as RefinedTreeNode) { i, value, refPart ->
            check(value is RefinedMappingNode)
            val valueProperty = value.refinedChildren[refPart]
            val propertyDeclaration = valueProperty?.propertyDeclaration
            val newValue = valueProperty?.value

            if (newValue == null) {
                reporter.reportBundleError(
                    origin.trace.asBuildProblemSource(),
                    "validation.reference.resolution.not.found", refPart
                )
                return null
            }
            if (i != referencePath.lastIndex && newValue !is RefinedMappingNode) {
                reporter.reportBundleError(
                    origin.trace.asBuildProblemSource(),
                    "validation.reference.resolution.not.a.mapping", refPart
                )
                return null
            }

            if (i == referencePath.lastIndex
                && propertyDeclaration != null && !propertyDeclaration.canBeReferenced
            ) {
                reporter.reportBundleError(
                    origin.trace.asBuildProblemSource(),
                    "validation.reference.resolution.not.referencable", refPart,
                )
                return null
            }

            newValue
        }

        if (resolved.deepContainsAnyReferences()) {
            // Skip for now, going to be resolved in the next pass.
            deferred += origin
            return null
        }

        return resolved
    }
}

/**
 * Conversion/cast rules (besides the trivial T <- T):
 * Traceable conversion:
 * - String <-> TraceableString
 * - Path <-> TraceablePath
 * - Enum <-> TraceableEnum
 *
 * Conversions:
 * - String <- Path, Enum, Int
 * - Path <- String
 */
private fun RefinedTreeNode.cast(
    targetType: SchemaType,
    trace: Trace = this.trace,
): RefinedTreeNode? {
    if (targetType.isMarkedNullable && this is NullLiteralNode)
        return this

    return when (targetType) {
        is SchemaType.EnumType if this is ScalarNode -> when (val type = type) {
            is SchemaType.EnumType if type.declaration == targetType.declaration -> {
                // Conversion is possible between `isTraceableWrapped = true/false`
                copyWithValue(value = value.unwrapTraceable().wrapTraceable(type, trace))
            }
            else -> null
        }
        is SchemaType.PathType if this is ScalarNode -> copyWithValue(
            value = when (val value = value.unwrapTraceable()) {
                is String -> Path(value)
                is Path -> value
                else -> return null
            }.wrapTraceable(targetType, trace)
        )
        is SchemaType.StringType if this is ScalarNode
                && type.stringSemantics() assignableTo targetType.semantics -> copyWithValue(
            value = when (val value = value.unwrapTraceable()) {
                is String -> value // also goes for external enums
                is SchemaEnum -> value.schemaValue
                is Path -> value.pathString
                is Int -> value.toString()
                else -> return null
            }.wrapTraceable(targetType, trace)
        )
        is SchemaType.BooleanType if this is ScalarNode && this.value is Boolean -> this
        is SchemaType.IntType if this is ScalarNode && this.value is Int -> this
        is SchemaType.ObjectType if this is RefinedMappingNode && declaration == targetType.declaration -> this
        is SchemaType.VariantType if this is RefinedMappingNode && declaration in targetType.declaration.variants -> this
        is SchemaType.ListType if this is RefinedListNode -> copy(
            children = children.map {
                it.cast(targetType.elementType) ?: return null
            }
        )
        is SchemaType.MapType if this is RefinedMappingNode && type is SchemaType.MapType -> copy (
            refinedChildren = refinedChildren.mapValues { (_, property) ->
                property.copyWithValue(value = property.value.cast(targetType.valueType) ?: return null)
            }
        )
        else -> null
    }
}

private fun SchemaType.stringSemantics() = when(this) {
    is SchemaType.StringType -> semantics
    else -> null // a potential string representation of everything else is a raw string
}

private infix fun SchemaType.StringType.Semantics?.assignableTo(other: SchemaType.StringType.Semantics?): Boolean {
    return other == null  // Everything is assignable to raw string
            || this == other
}

private fun renderTypeOf(value: TreeNode): String = when(value) {
    is NullLiteralNode -> "null"
    is ListNode -> value.type.withNullability(false).render(includeSyntax = false)
    is MappingNode -> value.type.withNullability(false).render(includeSyntax = false)
    is ScalarNode -> value.type.withNullability(false).render(includeSyntax = false)
    is ErrorNode -> "<no value>" // FIXME: Are no values legal here? Investigate
    is ReferenceNode, is StringInterpolationNode -> error("Not reached: must be resolved first")
}

private fun RefinedTreeNode.deepContainsAnyReferences(): Boolean = when(this) {
    is RefinedListNode -> children.any { it.deepContainsAnyReferences() }
    is RefinedMappingNode -> children.any { it.value.deepContainsAnyReferences() }
    is ReferenceNode, is StringInterpolationNode -> true
    is ScalarNode, is ErrorNode, is NullLiteralNode -> false
}

private fun ReferenceNode.resolvedTrace(resolvedValue: Traceable) = ResolvedReferenceTrace(
    description = $$"$${if (trace.isDefault) "default, " else ""}from ${$${referencedPath.joinToString(".")}}",
    referenceTrace = trace,
    resolvedValue = resolvedValue,
)

private fun Any.unwrapTraceable(): Any = when (this) {
    is TraceableValue<*> -> value!!
    else -> this
}

private fun Path.wrapTraceable(type: SchemaType.PathType, trace: Trace) =
    if (type.isTraceableWrapped) TraceablePath(this, trace) else this

private fun String.wrapTraceable(type: SchemaType.StringType, trace: Trace) =
    if (type.isTraceableWrapped) TraceableString(this, trace) else this

private fun Any.wrapTraceable(type: SchemaType.EnumType, trace: Trace) =
    if (type.isTraceableWrapped) TraceableEnum(this as Enum<*>, trace) else this