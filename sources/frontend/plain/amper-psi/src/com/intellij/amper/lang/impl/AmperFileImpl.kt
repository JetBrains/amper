package com.intellij.amper.lang.impl

import com.intellij.amper.lang.*
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class AmperFileImpl(viewProvider: FileViewProvider, language: Language)
  : PsiFileBase(viewProvider, language), AmperFile {
  override fun getFileType(): FileType {
    return getViewProvider().getFileType()
  }

  override fun toString(): String {
    return "AmperFile: " + getName()
  }

  override fun getConstructorReference(): AmperConstructorReference? {
    return null
  }

  override fun getObjectElementList(): List<AmperObjectElement> {
    return children.filterIsInstance<AmperObjectElement>()
  }

  override fun findProperty(name: String): AmperProperty? {
    return children.filterIsInstance<AmperProperty>().find { it.name == name }
  }
}