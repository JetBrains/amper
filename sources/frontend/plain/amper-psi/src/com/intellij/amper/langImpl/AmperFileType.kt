package com.intellij.amper.langImpl

import com.intellij.amper.lang.AmperLanguage
import com.intellij.icons.EmptyIcon
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import javax.swing.Icon

class AmperFileType private constructor() : LanguageFileType(AmperLanguage.INSTANCE) {
  override fun getName(): String {
    return "Amper"
  }

  override fun getDescription(): @NlsContexts.Label String {
    return "Amper"
  }

  override fun getDefaultExtension(): String {
    return DEFAULT_EXTENSION
  }

  override fun getIcon(): Icon {
    return EmptyIcon
  }

  companion object {
    @JvmField
    val INSTANCE: AmperFileType = AmperFileType()
    const val DEFAULT_EXTENSION: String = "amper"
  }
}