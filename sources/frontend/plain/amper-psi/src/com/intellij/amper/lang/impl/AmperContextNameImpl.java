// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.amper.lang.AmperElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.amper.lang.*;

public class AmperContextNameImpl extends ASTWrapperPsiElement implements AmperContextName {

  public AmperContextNameImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AmperElementVisitor visitor) {
    visitor.visitContextName(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AmperElementVisitor) accept((AmperElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
