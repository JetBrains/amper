package com.intellij.deft.codeInsight

import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.*

class DeftPlatformReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is YAMLKeyValue) return PsiReference.EMPTY_ARRAY

    val platformsText = element.keyText
      .substringAfter("@", missingDelimiterValue = "")
    if (platformsText.isEmpty()) return emptyArray()
    val platforms = platformsText.split("+")
    return platforms.map { platform ->
      val startOffset = element.keyText.indexOf(platform)
      check(startOffset != -1)
      DeftPlatformReference(platform, element, TextRange.from(startOffset, platform.length))
    }.toTypedArray()
  }
}

internal class DeftPlatformReference(val platformName: String, element: PsiElement, textRange: TextRange) : PsiReferenceBase<PsiElement>(
  element, textRange, false), HighlightedReference {
  override fun resolve(): PsiElement? {
    return element.containingFile
      .let { it as? YAMLFile }
      ?.findProductElement()
      ?.value
      ?.let { it as? YAMLMapping }
      ?.keyValues
      ?.find { it.keyText == "platforms" }
      ?.value
      ?.let { it as? YAMLSequence }
      ?.items
      ?.mapNotNull { it.value as? YAMLScalar }
      ?.find { it.textValue == platformName }
  }

  override fun getVariants(): Array<Any> {
    return element.getProduct()?.platforms?.map {
      LookupElementBuilder.create(it)
        .withIcon(AllIcons.General.Gear)
        .withTypeText("Platform")
    }?.toTypedArray() ?: ArrayUtilRt.EMPTY_OBJECT_ARRAY
  }
}
