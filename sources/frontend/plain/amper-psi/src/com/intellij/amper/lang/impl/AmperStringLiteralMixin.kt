package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperStringLiteral
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

open class AmperStringLiteralMixin(node: ASTNode): AmperStringLiteral, ContributedReferenceHost, AmperElementImpl(node) {
  override fun getReferences(): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }
}