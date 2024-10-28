// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang.impl;

import com.intellij.amper.lang.AmperElementVisitor;
import com.intellij.amper.lang.AmperReferenceExpression;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AmperReferenceExpressionImpl extends AmperReferenceExpressionMixin implements AmperReferenceExpression {

  public AmperReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AmperElementVisitor visitor) {
    visitor.visitReferenceExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AmperElementVisitor) accept((AmperElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<AmperReferenceExpression> getReferenceExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, AmperReferenceExpression.class);
  }

}
