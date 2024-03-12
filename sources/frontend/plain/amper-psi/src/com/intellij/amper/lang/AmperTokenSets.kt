package com.intellij.amper.lang

import com.intellij.psi.tree.TokenSet

object AmperTokenSets {
  val COMMENTS = TokenSet.create(AmperElementTypes.LINE_COMMENT, AmperElementTypes.BLOCK_COMMENT)
  val STRINGS = TokenSet.create(AmperElementTypes.STRING_LITERAL)
  val CONTAINERS = TokenSet.create(AmperElementTypes.OBJECT, AmperElementTypes.CONTEXT_BLOCK)
}