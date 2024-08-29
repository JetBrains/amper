package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperReferenceExpression
import com.intellij.amper.lang.AmperStringLiteral
import com.intellij.amper.lang.AmperValue
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement

open class AmperPropertyMixin(node: ASTNode): AmperElementImpl(node), AmperProperty {
  override fun setName(name: String): PsiElement {
    return this
  }

  override fun getName(): String? {
    return children.firstOrNull { it is AmperReferenceExpression || it is AmperStringLiteral }?.let {
      StringUtil.unquoteString(it.text)
    }
  }

  override fun getNameElement(): AmperValue? {
    return children.firstOrNull { it is AmperReferenceExpression || it is AmperStringLiteral } as? AmperValue
  }

  override fun getValue(): AmperValue? {
    val nameElement = nameElement
    return children.firstOrNull { it is AmperValue && it != nameElement } as? AmperValue
  }
}