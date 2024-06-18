/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.asAbsolutePath
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

/**
 * Extract all modifiers that are present within this scalar node.
 */
fun YAMLKeyValue.extractModifiers(): Modifiers =
  keyText.substringAfter("@", "")
    .split("+")
    .filter { it.isNotBlank() }
    .map { TraceableString(it).applyPsiTrace(key) }
    .toSet()
    .takeIf { it.isNotEmpty() } ?: noModifiers

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
fun YAMLScalar.asAbsolutePath(): Path = textValue.asAbsolutePath()

/**
 * Same as [String.asAbsolutePath], but accepts [YAMLKeyValue].
 */
context(ConvertCtx)
fun YAMLKeyValue.asAbsolutePath(): Path = keyText.asAbsolutePath()

/**
 * Creates a [TraceableString] from the text value of this [YAMLScalar].
 */
fun YAMLScalar.asTraceableString(): TraceableString = TraceableString(textValue).applyPsiTrace(this)
