package com.intellij.amper.lang

import com.intellij.lang.Language

class AmperLanguage : Language {

  companion object {
    @JvmStatic
    val INSTANCE: AmperLanguage = AmperLanguage()
  }

  private constructor(ID: String, vararg mimeTypes: String): super(INSTANCE, ID, *mimeTypes)

  private constructor(): super("Amper", "application/amper")

  override fun isCaseSensitive(): Boolean {
    return true
  }
}