package com.intellij.deft.codeInsight

import com.intellij.deft.icons.DeftIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class DeftIconProvider : IconProvider(), DumbAware {
  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (element !is PsiFile) return null

    return when {
      element.isPot() -> return DeftIcons.FileType
      element.isPotTemplate() -> return DeftIcons.TemplateFileType
      else -> null
    }
  }
}
