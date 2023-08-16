package org.jetbrains.deft.ide

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLFile

internal const val TEMPLATE_FILE_SUFFIX = ".Pot-template.yaml"
internal const val POT_FILE_NAME = "Pot.yaml"

internal fun VirtualFile.isPot(): Boolean = name == POT_FILE_NAME

internal fun VirtualFile.isPotTemplate(): Boolean = name.endsWith(TEMPLATE_FILE_SUFFIX)

internal fun String.stripTemplateSuffix(): String = removeSuffix(TEMPLATE_FILE_SUFFIX)

internal fun VirtualFile.isDeftFile(): Boolean = isPot() || isPotTemplate()

internal fun fromDeftFile(): PatternCondition<PsiElement> = object : PatternCondition<PsiElement>("from-deft-file") {
    override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean = t.containingFile.originalFile.isDeftFile()
}

internal fun PsiElement.isDeftFile(): Boolean = isPot() || isPotTemplate()

internal fun PsiElement.isPot(): Boolean {
    if (this !is YAMLFile) return false
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(this.virtualFile.isPot(), this)
    }
}

internal fun PsiElement.isPotTemplate(): Boolean {
    if (this !is YAMLFile) return false
    return CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result(this.virtualFile.isPotTemplate(), this)
    }
}
