package com.intellij.deft.codeInsight.yaml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.meta.model.YamlMetaType
import org.jetbrains.yaml.meta.model.YamlReferenceType
import org.jetbrains.yaml.psi.YAMLScalar

@Suppress("UnstableApiUsage")
internal class DeftFileValueReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    assert(element is YAMLScalar) { "contributor must assert this : $element" }

    val scalar = element as YAMLScalar
    val meta = DeftMetaTypeProvider.getInstance().getMetaTypeProxy(scalar)
    return when (val metaType: YamlMetaType? = meta?.metaType) {
      is YamlReferenceType -> metaType.getReferencesFromValue(scalar)
      else -> PsiReference.EMPTY_ARRAY
    }
  }
}
