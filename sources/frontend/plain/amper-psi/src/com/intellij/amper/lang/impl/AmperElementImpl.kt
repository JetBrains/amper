package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperElement
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

open class AmperElementImpl(node: ASTNode): ASTWrapperPsiElement(node), AmperElement {
  override fun toString(): String {
    val className = javaClass.simpleName
    return className.removeSuffix("Impl")
  }
}

