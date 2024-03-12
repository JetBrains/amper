// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.amper.lang.AmperElementTypes.*;
import com.intellij.amper.lang.*;

public class AmperContextualStatementImpl extends AmperContextualElementImpl implements AmperContextualStatement {

  public AmperContextualStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull AmperElementVisitor visitor) {
    visitor.visitContextualStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AmperElementVisitor) accept((AmperElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AmperContextName> getContextNameList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AmperContextName.class);
  }

  @Override
  @NotNull
  public AmperObjectElement getObjectElement() {
    return findNotNullChildByClass(AmperObjectElement.class);
  }

}
