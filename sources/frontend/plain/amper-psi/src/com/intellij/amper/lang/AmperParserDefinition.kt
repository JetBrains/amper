package com.intellij.amper.lang

import com.intellij.amper.lang.*
import com.intellij.amper.lang.impl.AmperFileImpl
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class AmperLexer : FlexAdapter(_AmperLexer())

class AmperParserDefinition: ParserDefinition {

  override fun createLexer(project: Project?): Lexer {
    return AmperLexer()
  }

  override fun createParser(project: Project?): PsiParser {
    return AmperParser()
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun getCommentTokens(): TokenSet {
    return AmperTokenSets.COMMENTS
  }

  override fun getStringLiteralElements(): TokenSet {
    return AmperTokenSets.STRINGS
  }

  override fun createElement(node: ASTNode?): PsiElement {
    return AmperElementTypes.Factory.createElement(node)
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return AmperFileImpl(viewProvider, AmperLanguage.INSTANCE)
  }
}

val FILE: IFileElementType = IFileElementType(AmperLanguage.INSTANCE)