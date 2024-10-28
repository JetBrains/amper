// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang.impl;

import com.intellij.amper.lang.AmperElementVisitor;
import com.intellij.amper.lang.AmperValue;
import com.intellij.amper.lang.AmperVariableDeclaration;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.amper.lang.AmperElementTypes.IDENTIFIER;

public class AmperVariableDeclarationImpl extends AmperObjectElementImpl implements AmperVariableDeclaration {

  public AmperVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull AmperElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AmperElementVisitor) accept((AmperElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AmperValue getValue() {
    return findChildByClass(AmperValue.class);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
