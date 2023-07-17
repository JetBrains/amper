package org.jetbrains.deft.ide.yaml

import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.tree.TokenSet
import org.jetbrains.yaml.YAMLElementTypes

internal class DeftFileReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val scalarTextPattern = psiElement()
            .withElementType(TokenSet.create(YAMLElementTypes.SCALAR_PLAIN_VALUE, YAMLElementTypes.SCALAR_QUOTED_STRING))
        registrar.registerReferenceProvider(scalarTextPattern, DeftFileValueReferenceProvider())
    }
}
