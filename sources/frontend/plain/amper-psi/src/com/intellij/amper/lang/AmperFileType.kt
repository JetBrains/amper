package com.intellij.amper.lang

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class AmperFileType private constructor() : LanguageFileType(AmperLanguage.INSTANCE) {
  override fun getName(): String {
    return "Amper"
  }

  override fun getDescription(): String {
    return "Amper"
  }

  override fun getDefaultExtension(): String {
    return DEFAULT_EXTENSION
  }

  override fun getIcon(): Icon {
    return AllIcons.FileTypes.Json
  }

  companion object {
    @JvmField
    val INSTANCE: AmperFileType = AmperFileType()
    const val DEFAULT_EXTENSION: String = "amper"
  }
}