package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperElementTypes
import com.intellij.amper.lang.AmperValue
import com.intellij.lang.ASTNode
import com.intellij.psi.util.*

open class AmperInvocationExpressionMixin(node: ASTNode): AmperElementImpl(node) {
  fun getArguments(): List<AmperValue> {
    val paren = this.childLeafs().firstOrNull { it.elementType == AmperElementTypes.L_PAREN } ?: return emptyList()
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AmperValue::class.java).filter { it.startOffset >= paren.endOffset }
  }
}