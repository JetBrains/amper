package com.intellij.amper.langImpl

import com.intellij.amper.icons.AmperIcons
import com.intellij.amper.lang.AmperLanguage
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
    return AmperIcons.FileType
  }

  companion object {
    @JvmField
    val INSTANCE: AmperFileType = AmperFileType()
    const val DEFAULT_EXTENSION: String = "amper"
  }
}