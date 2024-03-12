package com.intellij.amper.lang

import com.intellij.lang.Language

class AmperLanguage : Language {

  companion object {
    @JvmStatic
    val INSTANCE: AmperLanguage = AmperLanguage()
  }

  constructor(ID: String, vararg mimeTypes: String): super(INSTANCE, ID, *mimeTypes)

  constructor(): super("Amper", "application/amper")

  override fun isCaseSensitive(): Boolean {
    return true
  }
}