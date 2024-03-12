// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public class AmperElementVisitor extends PsiElementVisitor {

  public void visitBooleanLiteral(@NotNull AmperBooleanLiteral o) {
    visitLiteral(o);
  }

  public void visitConstructorReference(@NotNull AmperConstructorReference o) {
    visitPsiElement(o);
  }

  public void visitContainer(@NotNull AmperContainer o) {
    visitValue(o);
  }

  public void visitContextBlock(@NotNull AmperContextBlock o) {
    visitContextualElement(o);
  }

  public void visitContextName(@NotNull AmperContextName o) {
    visitPsiElement(o);
  }

  public void visitContextualElement(@NotNull AmperContextualElement o) {
    visitObjectElement(o);
  }

  public void visitContextualPropertyReference(@NotNull AmperContextualPropertyReference o) {
    visitValue(o);
  }

  public void visitContextualStatement(@NotNull AmperContextualStatement o) {
    visitContextualElement(o);
  }

  public void visitInvocationElement(@NotNull AmperInvocationElement o) {
    visitObjectElement(o);
  }

  public void visitInvocationExpression(@NotNull AmperInvocationExpression o) {
    visitValue(o);
  }

  public void visitLiteral(@NotNull AmperLiteral o) {
    visitValue(o);
  }

  public void visitNullLiteral(@NotNull AmperNullLiteral o) {
    visitLiteral(o);
  }

  public void visitNumberLiteral(@NotNull AmperNumberLiteral o) {
    visitLiteral(o);
  }

  public void visitObject(@NotNull AmperObject o) {
    visitContainer(o);
  }

  public void visitObjectElement(@NotNull AmperObjectElement o) {
    visitPsiElement(o);
  }

  public void visitProperty(@NotNull AmperProperty o) {
    visitObjectElement(o);
    // visitElement(o);
    // visitPsiNamedElement(o);
  }

  public void visitReferenceExpression(@NotNull AmperReferenceExpression o) {
    visitValue(o);
  }

  public void visitStringLiteral(@NotNull AmperStringLiteral o) {
    visitLiteral(o);
  }

  public void visitValue(@NotNull AmperValue o) {
    visitElement(o);
  }

  public void visitElement(@NotNull AmperElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
