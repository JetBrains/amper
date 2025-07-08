/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull


@UsedInIdePlugin
fun <TS : TreeState> TreeValue<TS>.lookupValueBy(psiElement: PsiElement) =
    TreePsiLookupVisitor<TS>(psiElement).visitValue(this)

/**
 * Finds a [TreeValue] element that is most specific for given [lookup].
 */
// TODO This is a pretty trivial implementation and possibly can be improved.
class TreePsiLookupVisitor<TS : TreeState>(
    private val lookup: PsiElement,
) : RecurringTreeVisitor<TreeValue<TS>?, TS>() {

    override fun aggregate(value: TreeValue<TS>, childResults: List<TreeValue<TS>?>) =
        childResults.firstNotNullOfOrNull { it } ?: value.checkSelf()

    override fun visitScalarValue(value: ScalarValue<TS>) = value.checkSelf()
    override fun visitReferenceValue(value: ReferenceValue<TS>) = value.checkSelf()

    override fun visitNoValue(value: NoValue) = null

    private fun TreeValue<TS>.checkSelf() = if (trace.extractPsiElementOrNull()?.contains(lookup) == true) this else null
    private operator fun PsiElement.contains(element: PsiElement) = textRange.contains(element.textRange)
}