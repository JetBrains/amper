/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull


@UsedInIdePlugin
fun TreeNode.lookupValueBy(psiElement: PsiElement) =
    TreePsiLookupVisitor(psiElement).visit(this)

/**
 * Finds a [TreeNode] element that is most specific for given [lookup].
 */
// TODO This is a pretty trivial implementation and possibly can be improved.
class TreePsiLookupVisitor(
    private val lookup: PsiElement,
) : RecurringTreeVisitor<TreeNode?>() {

    override fun aggregate(node: TreeNode, childResults: List<TreeNode?>) =
        childResults.firstNotNullOfOrNull { it } ?: node.checkSelf()

    override fun visitNull(node: NullLiteralNode) = node.checkSelf()
    override fun visitScalar(node: ScalarNode) = node.checkSelf()
    override fun visitReference(node: ReferenceNode) = node.checkSelf()
    override fun visitStringInterpolation(node: StringInterpolationNode) = node.checkSelf()

    override fun visitError(node: ErrorNode) = null

    private fun TreeNode.checkSelf() = if (trace.extractPsiElementOrNull()?.contains(lookup) == true) this else null
    private operator fun PsiElement.contains(element: PsiElement) = textRange.contains(element.textRange)
}