package org.jetbrains.deft.ide.yaml

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.tree.TokenSet
import org.jetbrains.deft.ide.fromDeftFile
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.psi.YAMLSequenceItem

internal class DeftFileReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val scalarTextPattern = psiElement()
            .with(fromDeftFile())
            .withElementType(
                TokenSet.create(YAMLElementTypes.SCALAR_PLAIN_VALUE, YAMLElementTypes.SCALAR_QUOTED_STRING)
            )
            .and(psiElement().withParent(YAMLSequenceItem::class.java))
        registrar.registerReferenceProvider(scalarTextPattern, DeftFileValueReferenceProvider())
    }
}
