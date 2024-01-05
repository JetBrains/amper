package org.jetbrains.amper.frontend.schemaConverter

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.convertModulePsi
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.convertTemplatePsi
import java.io.Reader
import java.nio.file.Path

const val USE_PSI_CONVERTER = false

context(ConvertCtx, ProblemReporterContext)
fun convertModule(usePsiConverter: Boolean = USE_PSI_CONVERTER, modulePath: Path): Module? {
  return if (usePsiConverter) {
    val psiFile = resolvePsiFile(modulePath)
    psiFile?.let { convertModulePsi(it) }
  } else {
    val reader = resolveReader(modulePath)
    reader?.let { convertModuleViaSnake{ it } }
  }
}

context(ConvertCtx, ProblemReporterContext)
fun convertTemplate(usePsiConverter: Boolean = USE_PSI_CONVERTER, templatePath: Path): Template? {
  return if (usePsiConverter) {
    val psiFile = resolvePsiFile(templatePath)
    psiFile?.let { convertTemplatePsi(it) }
  } else {
    val reader = resolveReader(templatePath)
    reader?.let { convertTemplateViaSnake{ it } }
  }
}

context(ConvertCtx, ProblemReporterContext)
private fun ConvertCtx.resolveReader(modulePath: Path): Reader? {
  val reader = pathResolver.path2Reader(modulePath)
  if (reader == null) {
    problemReporter.reportError("Reader is not resolved for path $modulePath", modulePath)
  }
  return reader
}

context(ConvertCtx, ProblemReporterContext)
private fun ConvertCtx.resolvePsiFile(modulePath: Path): PsiFile? {
  val psiFile = pathResolver.path2PsiFile(modulePath)
  if (psiFile == null) {
    problemReporter.reportError("PsiFile is not resolved for path $modulePath", modulePath)
  }
  return psiFile
}