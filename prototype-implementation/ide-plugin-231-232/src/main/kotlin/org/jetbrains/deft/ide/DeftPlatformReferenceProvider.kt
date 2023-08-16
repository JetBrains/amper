package org.jetbrains.deft.ide

import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

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

private class DeftPlatformReference(val platformName: String, element: PsiElement, textRange: TextRange) : PsiReferenceBase<PsiElement>(element, textRange, false), HighlightedReference {
    override fun resolve(): PsiElement? {
        val file = element.containingFile
        return PsiTreeUtil.findChildrenOfType(file, YAMLScalar::class.java).firstOrNull {
            it.textValue == platformName
        }
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile
        val platforms = PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java).firstOrNull {
            it.keyText == "platforms"
        }?.value as? YAMLSequence ?: return ArrayUtilRt.EMPTY_OBJECT_ARRAY
        return platforms.items.map {
            LookupElementBuilder.create(it.value!!.text)
                .withIcon(AllIcons.General.Gear)
                .withTypeText("Platform")
                .withTypeText(file.name)
        }.toTypedArray()
    }
}
