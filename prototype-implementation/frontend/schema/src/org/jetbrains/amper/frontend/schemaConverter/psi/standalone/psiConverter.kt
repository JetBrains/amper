package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.TreeElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.convertBase
import org.jetbrains.amper.frontend.schemaConverter.psi.convertModule
import org.jetbrains.yaml.YAMLElementTypes
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.YAMLParserDefinition
import org.jetbrains.yaml.lexer.YAMLFlexLexer
import org.jetbrains.yaml.parser.YAMLParser
import org.jetbrains.yaml.psi.YAMLDocument
import java.io.Reader

fun getPsiRawModel(reader: Reader): PsiElement {
  val project: Project = DummyProject.instance

  initPsiFileFactory({})

  val psiManager = PsiManagerImpl(project)
  val result = DummyHolderFactory.createHolder(psiManager, YAMLLanguage.INSTANCE, null)
  val holder = result.treeElement
  val builder: PsiBuilder =
    PsiBuilderImpl(
      project,
      YAMLParserDefinition(),
      YAMLFlexLexer(),
      holder,
      reader.readText()
    )
  val node: ASTNode = YAMLParser().parse(YAMLElementTypes.DOCUMENT, builder)
  holder.rawAddChildren(node as TreeElement)
  PsiFileFactoryImpl.markGenerated(result)
  return node.psi
}

context(ProblemReporterContext)
fun convertModulePsi(reader: Reader): Module {
  val rootNode = getPsiRawModel(reader)
  // TODO Add reporting.
  if (rootNode !is YAMLDocument) return Module()
  return rootNode.convertModule()
}

context(ProblemReporterContext)
fun convertTemplate(reader: Reader): Template {
  val rootNode = getPsiRawModel(reader)
  if (rootNode !is YAMLDocument) return Template()
  return rootNode.convertBase(Template())
}