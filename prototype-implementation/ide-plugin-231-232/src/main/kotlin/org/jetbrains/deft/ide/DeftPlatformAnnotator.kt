package org.jetbrains.deft.ide

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.yaml.psi.YAMLKeyValue

class DeftPlatformAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is YAMLKeyValue) return
        if (!element.containingFile.originalFile.isDeftFile()) return

        val platformsText = element.keyText
            .substringAfter("@", missingDelimiterValue = "")
        if (platformsText.isEmpty()) return
        val platforms = platformsText.split("+").filter(String::isNotBlank)

        platforms.forEach { platform ->
            val startOffset = element.keyText.indexOf(platform)
            check(startOffset != -1)
            val range = TextRange.from(element.startOffset + startOffset, platform.length)
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(DefaultLanguageHighlighterColors.STRING)
                .create()
        }
        element.keyText.forEachIndexed { i, c ->
            if (c == '@')
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange.from(element.startOffset + i, 1))
                    .textAttributes(DefaultLanguageHighlighterColors.COMMA)
                    .create()
            if (c == '+')
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(TextRange.from(element.startOffset + i, 1))
                    .textAttributes(DefaultLanguageHighlighterColors.NUMBER)
                    .create()
        }
    }
}
