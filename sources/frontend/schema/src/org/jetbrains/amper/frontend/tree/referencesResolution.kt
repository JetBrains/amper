/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TrivialTraceable
import org.jetbrains.amper.frontend.api.withComputedValueTrace
import org.jetbrains.amper.frontend.contexts.DefaultCtx
import org.jetbrains.amper.frontend.contexts.EmptyContexts


context(ProblemReporterContext)
fun MergedTree.resolveReferences() =
    TreeReferencesResolver(this@ProblemReporterContext).visitValue(this)!!

class TreeReferencesResolver(
    reporterCtx: ProblemReporterContext,
) : TreeTransformer<Merged>(), ProblemReporterContext by reporterCtx, TreeValueReporterCtx {
    val currentPath = ArrayDeque<MergedTree>()

    override fun visitValue(value: TreeValue<Merged>) = try {
        currentPath.addFirst(value)
        super.visitValue(value)
    } finally {
        currentPath.removeFirst()
    }

    override fun visitMapValue(value: MapLikeValue<Merged>): MergedTree? {
        val substituted = value.copy<ReferenceValue<Merged>> { _, v, oldProp ->
            substitute(v)?.map { resolvedVal ->
                // We are basically copying the reference and substituting the referenced value in each copy.
                oldProp.copy(
                    value = resolvedVal,
                    kTrace = oldProp.kTrace.withComputedValueTrace(TrivialTraceable(resolvedVal.trace)),
                )    
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
        val resolutionRoot = currentPath.firstOrNull { it.asMapLike?.get(refStart) != null }
            ?: return null // TODO Report here we had not found any referenced value.
        val resolved = refPath.fold(listOf(resolutionRoot), ::resolveReferencePart)

        // Adjust contexts if the reference was the DefaultValue.
        return if (value.trace is DefaultTrace) resolved.map { it.withContexts(it.contexts + DefaultCtx) } else resolved
    }

    // TODO Check that reference value will be resolved correctly for each context combination, from
    // TODO where the referenced value could come from.
    private fun resolveReferencePart(roots: List<MergedTree>, singleRef: String) =
        roots.flatMap {
            it.asMapLike?.get(singleRef)?.values ?: return emptyList<MergedTree>()
        } // TODO Report here we had not found any referenced value.
}