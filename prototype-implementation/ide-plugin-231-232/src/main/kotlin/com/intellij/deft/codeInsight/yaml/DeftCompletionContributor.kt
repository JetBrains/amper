package com.intellij.deft.codeInsight.yaml

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.deft.codeInsight.fromDeftFile
import com.intellij.deft.codeInsight.isDeftFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.refactoring.suggested.endOffset
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLKeyValue

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

  override fun beforeCompletion(context: CompletionInitializationContext) {
    val file = context.file
    if (!file.originalFile.isDeftFile()) {
      return
    }
    val element = file.findElementAt(context.startOffset) ?: return
    if ((element.parent as? YAMLKeyValue)?.keyText?.contains('@') == false) {
      return
    }
    context.offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, element.endOffset)
  }
}
