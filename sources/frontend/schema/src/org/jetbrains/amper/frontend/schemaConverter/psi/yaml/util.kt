/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.amper.lang.AmperFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.asAbsolutePath
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * Extract all modifiers that are present within this scalar node.
 */
fun MappingEntry.extractModifiers(): Modifiers =
  keyText?.substringAfter("@", "")
    ?.split("+")
    ?.filter { it.isNotBlank() }
    ?.map { TraceableString(it).applyPsiTrace(keyElement) }
    ?.toSet()
    ?.takeIf { it.isNotEmpty() } ?: noModifiers

context(ConvertCtx)
fun String.asAbsolutePath(): Path =
  this
    .replace("/", File.separator)
    .let {
      baseFile.toNioPath()
        .resolve(it)
        .absolute()
        .normalize()
        .apply {
          // TODO Report non-existent paths.
          if (!exists()) {

          }
        }
    }

/**
 * Same as [String.asAbsolutePath], but accepts [PsiElement].
 */
context(ConvertCtx)
fun PsiElement.asAbsolutePath(): Path = textValue.asAbsolutePath()

/**
 * Same as [String.asAbsolutePath], but accepts [MappingEntry].
 */
context(ConvertCtx)
fun MappingEntry.asAbsolutePath(): Path = keyText!!.asAbsolutePath()

/**
 * Creates a [TraceableString] from the text value of this [PsiElement].
 */
fun PsiElement.asTraceableString(): TraceableString = TraceableString(textValue).applyPsiTrace(this)

val PsiFile.topLevelValue get() = when (this) {
  is YAMLFile -> children.filterIsInstance<YAMLDocument>().firstOrNull()?.topLevelValue
  is AmperFile -> this
  else -> null
}