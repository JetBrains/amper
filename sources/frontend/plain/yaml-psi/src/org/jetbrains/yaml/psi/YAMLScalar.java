/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.yaml.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public interface YAMLScalar extends YAMLValue, PsiLanguageInjectionHost {
  @NotNull
  @NlsSafe
  String getTextValue();

  boolean isMultiline();
}
