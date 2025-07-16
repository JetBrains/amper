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

fun MergedTree.resolveReferences(): TreeValue<Merged> {
    var value: TreeValue<Merged> = this
    var resolver: TreeReferencesResolver
    do {
        resolver = TreeReferencesResolver()
        value = resolver.visitValue(value)!!
    } while (resolver.referencesResolved > 0)
    // TODO: Check if there are yet unresolved references. It means that there are cycles in the referencing.
    return value
}

private class TreeReferencesResolver : TreeTransformer<Merged>() {

    private val currentPath = ArrayDeque<MergedTree>()

    var referencesResolved = 0
        private set

    override fun visitValue(value: TreeValue<Merged>): TreeValue<Merged>? = try {
        currentPath.addFirst(value)
        super.visitValue(value)
    } finally {
        currentPath.removeFirst()
    }

    override fun visitMapValue(value: MapLikeValue<Merged>): MergedTree? {
        val substituted = value.copy<Merged, ReferenceValue<Merged>> { _, v, oldProp ->
            oldProp.pType
            substitute(v)?.map { resolvedVal ->
                if (AreThereAnyReferences().visitValue(resolvedVal)) {
                    oldProp  // Incomplete - postpone until fully resolved
                } else {
                    // We are basically copying the reference and substituting the referenced value in each copy.
                    oldProp.copy(
                        value = resolvedVal,
                        kTrace = oldProp.kTrace.withComputedValueTrace(TrivialTraceable(resolvedVal.trace)),
                    ).also { referencesResolved++ }
                }
            }

        }
        return super.visitMapValue(substituted)
    }

    override fun visitListValue(value: ListValue<Merged>): MergedTree? {
        val substituted = value.copy<ReferenceValue<Merged>> { substitute(it) }
        return super.visitListValue(substituted)
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
                @Suppress("UNCHECKED_CAST")
                when (it) {
                    is ScalarValue<*> -> when (value.type) {
                        is SchemaType.PathType -> it.copy(value = Path("${value.prefix}${it.value}${value.suffix}")) as TreeValue<Merged>
                        is SchemaType.StringType -> it.copy(value = "${value.prefix}${it.value}${value.suffix}") as TreeValue<Merged>
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

private class AreThereAnyReferences : RecurringTreeVisitor<Boolean, Merged>() {
    override fun visitScalarValue(value: ScalarValue<Merged>) = false
    override fun visitNoValue(value: NoValue) = false
    override fun visitReferenceValue(value: ReferenceValue<Merged>) = true
    override fun aggregate(
        value: TreeValue<Merged>,
        childResults: List<Boolean>,
    ) = childResults.reduce(Boolean::or)
}