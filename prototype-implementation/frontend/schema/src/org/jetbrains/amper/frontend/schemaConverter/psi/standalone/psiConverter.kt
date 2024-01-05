package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.LocalTimeCounter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertBase
import org.jetbrains.amper.frontend.schemaConverter.psi.convertModule
import org.jetbrains.annotations.NonNls
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLDocument
import java.io.Reader
import javax.swing.Icon

fun getPsiRawModel(reader: Reader): PsiFile {
  val project: Project = DummyProject.instance

  initPsiFileFactory({}, project)

  val psiManager = PsiManagerImpl(project)
  val text = reader.readText().replace("\r\n", "\n")
  val psiFile = createPsiFileFromText(text, psiManager)
  PsiFileFactoryImpl.markGenerated(psiFile)
  return psiFile
}

private fun createPsiFileFromText(text: String, manager: PsiManager): PsiFile {
  val virtualFile: @NonNls LightVirtualFile = LightVirtualFile("foo", object : LanguageFileType(YAMLLanguage.INSTANCE) {
    override fun getDefaultExtension(): @NonNls String {
      return ""
    }

    override fun getDescription(): @NonNls String {
      return "fake for language" + YAMLLanguage.INSTANCE.id
    }

    override fun getIcon(): Icon {
      return YAMLFileType.YML.icon
    }

    override fun getName(): @NonNls String {
      return YAMLLanguage.INSTANCE.id
    }
  }, text, LocalTimeCounter.currentTime())

  val viewProvider: FileViewProvider = object : SingleRootFileViewProvider(
    manager,
    virtualFile, false
  ) {
    override fun getBaseLanguage(): Language {
      return YAMLLanguage.INSTANCE
    }
  }

  return viewProvider.getPsi(YAMLLanguage.INSTANCE)
}

context(ProblemReporterContext, ConvertCtx)
fun convertModulePsi(reader: () -> Reader): Module {
  val psiFile = getPsiRawModel(reader())
  return convertModulePsi(psiFile)
}

context(ProblemReporterContext, ConvertCtx)
fun convertModulePsi(psiFile: PsiFile): Module {
  val rootNode = psiFile.children.first()
  // TODO Add reporting.
  if (rootNode !is YAMLDocument) return Module()
  return rootNode.convertModule()
}


context(ProblemReporterContext, ConvertCtx)
fun convertTemplatePsi(reader: () -> Reader): Template {
  val psiFile = getPsiRawModel(reader())
  return convertTemplatePsi(psiFile)
}

context(ProblemReporterContext, ConvertCtx)
fun convertTemplatePsi(psiFile: PsiFile): Template {
  val rootNode = psiFile.children.first()
  if (rootNode !is YAMLDocument) return Template()
  return rootNode.convertBase(Template())
}
