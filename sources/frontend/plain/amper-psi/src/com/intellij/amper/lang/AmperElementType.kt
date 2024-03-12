package com.intellij.amper.lang

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NonNls

class AmperElementType(@NonNls debugName: String) : IElementType(debugName, AmperLanguage.INSTANCE)

class AmperTokenType(@NonNls debugName: String) : IElementType(debugName, AmperLanguage.INSTANCE)


