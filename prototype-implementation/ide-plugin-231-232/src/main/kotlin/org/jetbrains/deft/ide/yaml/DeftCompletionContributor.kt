package org.jetbrains.deft.ide.yaml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.deft.ide.fromDeftFile
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLTokenTypes

internal class DeftCompletionContributor : CompletionContributor() {
    init {
        val insertedKey = psiElement().withParent(
            psiElement().withElementType(
                TokenSet.create(
                    YAMLElementTypes.SCALAR_PLAIN_VALUE,
                    YAMLElementTypes.SCALAR_QUOTED_STRING
                )
            )
        )
        val updatedKey = psiElement(YAMLTokenTypes.SCALAR_KEY)
        val scalarTextPattern = psiElement()
            .andOr(insertedKey, updatedKey)
            .with(fromDeftFile())

        extend(CompletionType.BASIC, scalarTextPattern, DeftCompletionProvider())
    }
}
