package com.intellij.amper.lang.impl

import com.intellij.amper.lang.AmperStringLiteral
import com.intellij.lang.ASTNode

open class AmperStringLiteralMixin(node: ASTNode): AmperStringLiteral, AmperElementImpl(node) {
}