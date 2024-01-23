/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.yaml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;

import java.util.function.Supplier;

public class YAMLParserUtil {

  private static final TokenSet BLANK_LINE_ELEMENTS = TokenSet.andNot(YAMLElementTypes.BLANK_ELEMENTS, YAMLElementTypes.EOL_ELEMENTS);

  /**
   * Copy-pasted from org.jetbrains.yaml.YAMLUtil (in order to avoid bringing dependencies of YAMLUtil to yaml.psi.api)
   */
  public static int getIndentToThisElement(@NotNull PsiElement element) {
    if (element instanceof YAMLBlockMappingImpl) {
      try {
        element = ((YAMLBlockMappingImpl)element).getFirstKeyValue();
      } catch (IllegalStateException e) {
        // Spring Boot plug-in modifies PSI-tree into invalid state
        // This is workaround over EA-133507 IDEA-210113
        if (!e.getMessage().equals(YAMLBlockMappingImpl.EMPTY_MAP_MESSAGE)) {
          throw e;
        }
        else {
          Logger.getInstance(YAMLParserUtil.class).warn(YAMLBlockMappingImpl.EMPTY_MAP_MESSAGE);
        }
      }
    }
    int offset = element.getTextOffset();

    PsiElement currentElement = element;
    while (currentElement != null) {
      final IElementType type = currentElement.getNode().getElementType();
      if (YAMLElementTypes.EOL_ELEMENTS.contains(type)) {
        return offset - currentElement.getTextOffset() - currentElement.getTextLength();
      }

      currentElement = PsiTreeUtil.prevLeaf(currentElement);
    }
    return offset;
  }

  public static int getIndentInThisLine(@NotNull final PsiElement elementInLine) {
    PsiElement currentElement = elementInLine;
    while (currentElement != null) {
      final IElementType type = currentElement.getNode().getElementType();
      if (type == YAMLTokenTypes.EOL) {
        return 0;
      }
      if (type == YAMLTokenTypes.INDENT) {
        return currentElement.getTextLength();
      }

      currentElement = PsiTreeUtil.prevLeaf(currentElement);
    }
    return 0;
  }

  public static PsiElement rename(final YAMLKeyValue element, final String newName) {
    if (newName.equals(element.getName())) {
      throw new IncorrectOperationException(YAMLBundle.message("rename.same.name"));
    }
    final YAMLKeyValue topKeyValue = YAMLElementGenerator.getInstance(element.getProject()).createYamlKeyValue(newName, "Foo");

    final PsiElement key = element.getKey();
    if (key == null || topKeyValue.getKey() == null) {
      throw new IllegalStateException();
    }
    key.replace(topKeyValue.getKey());
    return element;
  }

  public static void deleteSurroundingWhitespace(@NotNull final PsiElement element) {
    if (element.getNextSibling() != null) {
      deleteElementsOfType(element::getNextSibling, BLANK_LINE_ELEMENTS);
      deleteElementsOfType(element::getNextSibling, YAMLElementTypes.SPACE_ELEMENTS);
    }
    else {
      deleteElementsOfType(element::getPrevSibling, YAMLElementTypes.SPACE_ELEMENTS);
    }
  }

  private static void deleteElementsOfType(@NotNull final Supplier<? extends PsiElement> element, @NotNull final TokenSet types) {
    while (element.get() != null && types.contains(PsiUtilCore.getElementType(element.get()))) {
      element.get().delete();
    }
  }
}
