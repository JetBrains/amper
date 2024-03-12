// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AmperContextStatement extends AmperObjectElement {

  @NotNull
  List<AmperContextName> getContextNameList();

  @Nullable
  AmperProperty getProperty();

}
