package com.intellij.amper.lang.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

open class AmperReferenceExpressionMixin(node: ASTNode): AmperElementImpl(node) {
  override fun getReferences(): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }
}