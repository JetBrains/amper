package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperElementTypes
import com.intellij.lexer.LayeredLexer
import com.intellij.lexer.Lexer
import com.intellij.lexer.StringLiteralLexer
import com.intellij.psi.tree.IElementType

class AmperHighlightingLexer(baseLexer: Lexer?) : LayeredLexer(baseLexer) {
  init {
    registerSelfStoppingLayer(StringLiteralLexer('\"', AmperElementTypes.DOUBLE_QUOTED_STRING),
                              arrayOf<IElementType>(AmperElementTypes.DOUBLE_QUOTED_STRING), IElementType.EMPTY_ARRAY)
    registerSelfStoppingLayer(StringLiteralLexer('\'', AmperElementTypes.SINGLE_QUOTED_STRING),
                              arrayOf<IElementType>(AmperElementTypes.SINGLE_QUOTED_STRING), IElementType.EMPTY_ARRAY)
  }
}

