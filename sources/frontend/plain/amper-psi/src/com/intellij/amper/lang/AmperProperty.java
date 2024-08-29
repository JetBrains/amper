/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public interface AmperProperty extends AmperObjectElement, AmperElement, PsiNamedElement {

  @Nullable
  String getName();

  @Nullable
  AmperValue getNameElement();

  @Nullable
  AmperValue getValue();

  //WARNING: getPresentation(...) is skipped
  //matching getPresentation(AmperProperty, ...)
  //methods are not found in AmperPsiImplUtils

}
