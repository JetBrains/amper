/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.standalone

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.LocalTimeCounter
import org.jetbrains.annotations.NonNls
import java.io.Reader

fun getPsiRawModel(reader: Reader, type: LanguageFileType): PsiFile {
  val project: Project = DummyProject.instance

  initPsiFileFactory({}, project)

  val psiManager = PsiManagerImpl(project)
  val text = reader.readText().replace("\r\n", "\n")
  val psiFile = createPsiFileFromText(text, psiManager, type)
  PsiFileFactoryImpl.markGenerated(psiFile)
  return psiFile
}

private fun createPsiFileFromText(text: String, manager: PsiManager, type: LanguageFileType): PsiFile {
  val virtualFile: @NonNls LightVirtualFile = LightVirtualFile("foo", type, text, LocalTimeCounter.currentTime())

  val viewProvider: FileViewProvider = object : SingleRootFileViewProvider(
    manager,
    virtualFile, false
  ) {
    override fun getBaseLanguage(): Language {
      return type.language
    }
  }

  return viewProvider.getPsi(type.language)
}
