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
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.tree.reading.copyWithTrace
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.render
import org.jetbrains.amper.frontend.types.withNullability
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.problems.reporting.replayProblemsTo
import org.jetbrains.amper.stdlib.collections.IdentityHashSet
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

/**
 * Resolves [ReferenceValue]s and [StringInterpolationValue]s in the tree and returns the substituted result.
 * The returned value may still contain unresolved references,
 * but they all are guaranteed to have been reported as errors.
 */
context(buildCtx: BuildCtx)
internal fun Refined.resolveReferences(): Refined {
    val reporter = CollectingProblemReporter()

    var value: Refined = this
    var resolver: TreeReferencesResolver? = null
    do {
        if (resolver != null && resolver.deferred.isEmpty()) {
            // Nothing more to resolve
            break
        }
        resolver = TreeReferencesResolver(
            reporter = reporter,
            resolveOnly = resolver?.deferred?.let { IdentityHashSet(it) }
        )
        when (val transformed = resolver.visitValue(value)) {
            is Changed<*> -> {
                value = transformed.value as Refined
            }
            NotChanged -> break  // No more changes made - stop the resolution process
            Removed -> error("Not reached")
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
    private val resolveOnly: Set<TreeValue<*>>? = null,
) : TreeTransformer<Refined>() {
    val deferred = mutableListOf<TreeValue<*>>()

    private val currentPath = mutableListOf<MapLikeValue<Refined>>()

    override fun visitMapValue(value: MapLikeValue<Refined>): TransformResult<MapLikeValue<Refined>> {
        currentPath.add(value)
        return super.visitMapValue(value).also {
            currentPath.removeLast()
        }
    }

    override fun visitReferenceValue(value: ReferenceValue<Refined>): TransformResult<TreeValue<Refined>> {
        if (resolveOnly != null && value !in resolveOnly) {
            return NotChanged
        }

        val resolved = resolve(value, value.referencedPath) ?: return NotChanged
        val converted = resolved.cast(targetType = value.type) ?: run {
            reporter.reportBundleError(
                value.trace.asBuildProblemSource(), "validation.reference.unexpected.type",
                renderTypeOf(resolved), value.type.render(includeSyntax = false)
            )
            return NotChanged
        }
        return Changed(converted.copyWithTrace(value.resolvedTrace(converted)))
    }

    override fun visitStringInterpolationValue(value: StringInterpolationValue<Refined>): TransformResult<TreeValue<Refined>> {
        if (resolveOnly != null && value !in resolveOnly) {
            return NotChanged
        }

        val allResolvedValues = mutableListOf<Traceable>()
        val interpolated = value.parts.map { part ->
            when (part) {
                is StringInterpolationValue.Part.Reference -> {
                    val resolved = resolve(value, part.referencePath) ?: return NotChanged
                    val converted = resolved.cast(SchemaType.StringType) ?: run {
                        reporter.reportBundleError(
                            value.trace.asBuildProblemSource(), "validation.reference.unexpected.type.interpolation",
                            renderTypeOf(resolved),
                        )
                        return NotChanged
                    }
                    allResolvedValues += converted
                    (converted as ScalarValue).value.toString()
                }
                is StringInterpolationValue.Part.Text -> part.text
            }
        }.joinToString("")

        val typedInterpolated = when (value.type) {
            is SchemaType.PathType -> try {
                Path(interpolated)
            } catch (e: InvalidPathException) {
                reporter.reportBundleError(value.trace.asBuildProblemSource(), "validation.types.invalid.path", e.message)
                return NotChanged
            }
            is SchemaType.StringType -> interpolated
        }
        val trace = TransformedValueTrace(
            description = "string interpolation: ${value.parts.joinToString("") { 
                when(it) {
                    is StringInterpolationValue.Part.Reference -> $$"${$${it.referencePath.joinToString(".")}}"
                    is StringInterpolationValue.Part.Text -> it.text
                }
            }}",
            definitionTrace = value.trace,
            // TODO: Support multi-source traces
            // first() is safe because of StringInterpolationValue contract.
            sourceValue = allResolvedValues.first(),
        )
        return Changed(ScalarValue(typedInterpolated, value.type, trace, value.contexts))
    }

    private fun resolve(
        origin: TreeValue<Refined>,
        referencePath: List<String>,
    ): TreeValue<Refined>? {
        val firstElement = referencePath.first()
        val reversedPath = currentPath.asReversed()

        val resolutionRoot = reversedPath.find { it[firstElement].isNotEmpty() } ?: run {
            reporter.reportBundleError(
                origin.trace.asBuildProblemSource(), "validation.reference.resolution.root.not.found",
                arguments = arrayOf(firstElement),
            )
            return null
        }

        val resolved = referencePath.foldIndexed(resolutionRoot as TreeValue<Refined>?) { i, value, refPart ->
            check(value is Refined)
            val newValue = value.refinedChildren[refPart]?.value

            if (newValue == null) {
                reporter.reportBundleError(
                    origin.trace.asBuildProblemSource(),
                    "validation.reference.resolution.not.found", refPart
                )
                return null
            }
            if (i != referencePath.lastIndex && newValue !is Refined) {
                reporter.reportBundleError(
                    origin.trace.asBuildProblemSource(),
                    "validation.reference.resolution.not.a.mapping", refPart
                )
                return null
            }

            newValue
        }

        if (resolved != null && resolved.deepContainsAnyReferences()) {
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
private fun <TS : TreeState> TreeValue<TS>.cast(targetType: SchemaType): TreeValue<TS>? {
    if (targetType.isMarkedNullable && this is NullValue)
        return this

    return when (targetType) {
        is SchemaType.EnumType if this is ScalarValue -> when (val type = type) {
            is SchemaType.EnumType if type.declaration == targetType.declaration -> {
                // Conversion is possible between `isTraceableWrapped = true/false`
                copy(value = value.unwrapTraceable().wrapTraceable(type, trace))
            }
            else -> null
        }
        is SchemaType.PathType if this is ScalarValue -> copy(
            value = when (val value = value.unwrapTraceable()) {
                is String -> Path(value)
                is Path -> value
                else -> return null
            }.wrapTraceable(targetType, trace)
        )
        is SchemaType.StringType if this is ScalarValue -> copy(
            value = when (val value = value.unwrapTraceable()) {
                is String -> value // also goes for external enums
                is SchemaEnum -> value.schemaValue
                is Path -> value.pathString
                is Int -> value.toString()
                else -> return null
            }.wrapTraceable(targetType, trace)
        )
        is SchemaType.BooleanType if this is ScalarValue && this.value is Boolean -> this
        is SchemaType.IntType if this is ScalarValue && this.value is Int -> this
        is SchemaType.ObjectType if this is MapLikeValue && declaration == targetType.declaration -> this
        is SchemaType.VariantType if this is MapLikeValue && declaration in targetType.declaration.variants -> this
        is SchemaType.ListType if this is ListValue -> copy(
            children = children.map {
                it.cast(targetType.elementType) ?: return null
            }
        )
        is SchemaType.MapType if this is MapLikeValue && type is SchemaType.MapType -> copy (
            children = children.map { property ->
                property.copy(newValue = property.value.cast(targetType.valueType) ?: return null)
            }
        )
        else -> null
    }
}

private fun renderTypeOf(value: TreeValue<*>): String = when(value) {
    is NullValue -> "null"
    is ListValue -> value.type.withNullability(false).render(includeSyntax = false)
    is MapLikeValue -> value.type.withNullability(false).render(includeSyntax = false)
    is ScalarValue -> value.type.withNullability(false).render(includeSyntax = false)
    is NoValue -> "<no value>" // FIXME: Are no values legal here? Investigate
    is ReferenceValue, is StringInterpolationValue -> error("Not reached: must be resolved first")
}

private fun TreeValue<Refined>.deepContainsAnyReferences(): Boolean = when(this) {
    is ListValue -> children.any { it.deepContainsAnyReferences() }
    is MapLikeValue -> children.any { it.value.deepContainsAnyReferences() }
    is ReferenceValue, is StringInterpolationValue -> true
    is ScalarValue, is NoValue, is NullValue -> false
}

private fun ReferenceValue<*>.resolvedTrace(resolvedValue: Traceable) = ResolvedReferenceTrace(
    description = $$"$${if (trace.isDefault) "default, " else ""}from ${$$referencedPath}",
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