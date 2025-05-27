/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import com.intellij.amper.lang.AmperProperty
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue


fun PsiElement.extractKeyElement(): PsiElement = when (this) {
    is YAMLKeyValue -> key ?: this
    is AmperProperty -> nameElement ?: this
    else -> this
}