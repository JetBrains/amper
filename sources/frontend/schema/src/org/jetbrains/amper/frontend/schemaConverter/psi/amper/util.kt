/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperContextBlock
import com.intellij.amper.lang.AmperContextualElement
import com.intellij.amper.lang.AmperContextualStatement
import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperProperty
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists


/**
 * Extract all modifiers that are present within this scalar node.
 */
fun AmperProperty.extractModifiers(): Modifiers {
  val name = nameElement ?: return noModifiers
  val modifiers = mutableSetOf<TraceableString>()
  var parentContext = PsiTreeUtil.getParentOfType(this, AmperContextualElement::class.java, true)
  while (parentContext != null) {
    modifiers.addAll(when (parentContext) {
      is AmperContextBlock -> parentContext.contextNameList
      is AmperContextualStatement -> parentContext.contextNameList
      else -> emptyList()
    }.mapNotNull { it.identifier?.let { ident -> TraceableString(ident.text).adjustTrace(name) } })
    parentContext = PsiTreeUtil.getParentOfType(parentContext, AmperContextualElement::class.java, true)
  }
  return modifiers
}

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
 * Same as [String.asAbsolutePath], but accepts [YAMLScalar].
 */
context(ConvertCtx)
fun AmperLiteral.asAbsolutePath(): Path = textValue.asAbsolutePath()

/**
 * Same as [String.asAbsolutePath], but accepts [YAMLKeyValue].
 */
context(ConvertCtx)
fun AmperProperty.asAbsolutePath(): Path = name!!.asAbsolutePath()

/**
 * Adjust this element trace.
 */
fun <T : Traceable> T.adjustTrace(it: PsiElement?) = apply { trace = it?.let(::PsiTrace) }


val AmperLiteral.textValue get() = StringUtil.unquoteString(text)