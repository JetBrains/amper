package com.intellij.deft.codeInsight

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import org.jetbrains.yaml.psi.YAMLKeyValue

class DeftPlatformReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(YAMLKeyValue::class.java).with(fromDeftFile()),
                                        DeftPlatformReferenceProvider())
  }
}
