package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperContextName
import com.intellij.amper.lang.AmperElementTypes
import com.intellij.lang.ASTNode
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry

open class AmperContextNameMixin(node: ASTNode): AmperElementImpl(node), ContributedReferenceHost, AmperContextName {
  override fun getIdentifier(): PsiElement? {
    return findChildByType(AmperElementTypes.IDENTIFIER)
  }
  override fun getReferences(): Array<PsiReference> {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this)
  }
}