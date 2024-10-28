// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface AmperObject extends AmperContainer {

  @Nullable
  AmperConstructorReference getConstructorReference();

  @NotNull
  List<AmperObjectElement> getObjectElementList();

  @Nullable AmperProperty findProperty(@NotNull String name);

  //WARNING: getPresentation(...) is skipped
  //matching getPresentation(AmperObject, ...)
  //methods are not found in AmperPsiImplUtils

}
