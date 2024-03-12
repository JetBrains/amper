// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AmperContextBlock extends AmperContextualElement {

  @NotNull
  List<AmperContextName> getContextNameList();

  @NotNull
  List<AmperObjectElement> getObjectElementList();

}
