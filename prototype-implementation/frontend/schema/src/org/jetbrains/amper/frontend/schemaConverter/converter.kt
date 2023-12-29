package org.jetbrains.amper.frontend.schemaConverter

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.convertModulePsi
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.convertTemplatePsi
import java.io.Reader

const val USE_PSI_CONVERTER = false

context(ConvertCtx, ProblemReporterContext)
fun convertModule(usePsiConverter: Boolean = USE_PSI_CONVERTER, reader: () -> Reader): Module {
  return if (usePsiConverter) {
    convertModulePsi(reader)
  } else {
    convertModuleViaSnake(reader)
  }
}

context(ConvertCtx, ProblemReporterContext)
fun convertTemplate(usePsiConverter: Boolean = USE_PSI_CONVERTER, reader: () -> Reader): Template {
  return if (usePsiConverter) {
    convertTemplatePsi(reader)
  } else {
    convertTemplateViaSnake(reader)
  }
}