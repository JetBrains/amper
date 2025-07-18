/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TrivialTraceable
import org.jetbrains.amper.frontend.api.withComputedValueTrace
import org.jetbrains.amper.frontend.contexts.DefaultCtx
import org.jetbrains.amper.frontend.types.SchemaType
import kotlin.io.path.Path

fun Merged.resolveReferences(): Merged {
    var value: Merged = this
    var resolver: TreeReferencesResolver
    do {
        resolver = TreeReferencesResolver()
        value = when (val newValue = resolver.visitValue(value)) {
            is Changed<*> -> newValue.value as Merged
            NotChanged -> return value
            Removed -> return value
        }
    } while (true)
    // TODO: Check if there are any cycles.
}

private class TreeReferencesResolver : TreeTransformer<Merged>() {

    private val currentPath = ArrayDeque<MergedTree>()

    override fun visitValue(value: TreeValue<Merged>): TransformResult<TreeValue<Merged>> = try {
        currentPath.addFirst(value)
        super.visitValue(value)
    } finally {
        currentPath.removeFirst()
    }

    override fun visitMapValue(value: MapLikeValue<Merged>): TransformResult<MapLikeValue<Merged>> {
        // Skip nodes without references.
        if (value.children.none { it.value is ReferenceValue<*> }) return super.visitMapValue(value)

        val substituted = value.copy<Merged, ReferenceValue<Merged>> { _, v, oldProp ->
            substitute(v)?.map { resolvedVal ->
                // TODO We need to preserve resolution root to escape this check. Need
                //  to think carefully about resolution once again.
                if (resolvedVal.areThereAnyReferences()) {
                    // Incomplete - postpone until fully resolved
                    oldProp
                } else {
                    // We are basically copying the reference and substituting the referenced value in each copy.
                    oldProp.copy(
                        value = resolvedVal,
                        kTrace = oldProp.kTrace.withComputedValueTrace(TrivialTraceable(resolvedVal.trace)),
                    )
                }
            }

        }
        return when (val transformedSubstituted = super.visitMapValue(substituted)) {
            is Changed -> Changed(transformedSubstituted.value)
            NotChanged -> Changed(substituted)
            Removed -> Removed
        }
    }

    override fun visitListValue(value: ListValue<Merged>): TransformResult<TreeValue<Merged>> {
        if (value.children.none { it is ReferenceValue<*> }) return super.visitListValue(value)

        val substitutedChildren = value.children.flatMap {
            if (it is ReferenceValue) substitute(it).orEmpty()
            else listOfNotNull(transform(it))
        }
        return Changed(value.copy(children = substitutedChildren))
    }

    private fun substitute(value: ReferenceValue<Merged>): List<MergedTree>? {
        val refPath = value.referencedPath.split(".")
        val refStart = refPath.first()
        val resolutionRoot = currentPath.firstOrNull { !it.asMapLike?.get(refStart).isNullOrEmpty() }
            ?: return null // TODO Report here we had not found any referenced value.
        var resolved = refPath.fold(listOf(resolutionRoot), ::resolveReferencePart)

        if (value.prefix.isNotEmpty() || value.suffix.isNotEmpty()) {
            resolved = resolved.map {
                // Do poor man's string interpolation
                when (it) {
                    is ScalarValue -> when (value.type) {
                        is SchemaType.PathType -> it.copy(value = Path("${value.prefix}${it.value}${value.suffix}"))
                        is SchemaType.StringType -> it.copy(value = "${value.prefix}${it.value}${value.suffix}")
                        else -> it
                    }
                    else -> it
                }
            }
        }

        if (value.trace is DefaultTrace) {
            // Adjust contexts if the reference was the DefaultValue.
            resolved = resolved.map { it.withContexts(it.contexts + DefaultCtx) }
        }

        return resolved
    }

    // TODO Check that reference value will be resolved correctly for each context combination, from
    // TODO where the referenced value could come from.
    private fun resolveReferencePart(roots: List<MergedTree>, singleRef: String) =
        roots.flatMap {
            it.asMapLike?.get(singleRef)?.values ?: error("Unable to resolve $singleRef in $roots")
        } // TODO Report here we had not found any referenced value.
}

private fun MergedTree.areThereAnyReferences() = AreThereAnyReferences.visitValue(this)

private object AreThereAnyReferences : RecurringTreeVisitor<Boolean, Merged>() {
    override fun visitScalarValue(value: ScalarValue<Merged>) = false
    override fun visitNoValue(value: NoValue) = false
    override fun visitReferenceValue(value: ReferenceValue<Merged>) = true
    override fun aggregate(
        value: TreeValue<Merged>,
        childResults: List<Boolean>,
    ) = childResults.reduce(Boolean::or)
}