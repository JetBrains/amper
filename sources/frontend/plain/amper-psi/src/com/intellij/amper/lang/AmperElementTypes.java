// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.amper.lang.impl.*;

public interface AmperElementTypes {

  IElementType BOOLEAN_LITERAL = new AmperElementType("BOOLEAN_LITERAL");
  IElementType CONSTRUCTOR_REFERENCE = new AmperElementType("CONSTRUCTOR_REFERENCE");
  IElementType CONTEXTUAL_ELEMENT = new AmperElementType("CONTEXTUAL_ELEMENT");
  IElementType CONTEXTUAL_PROPERTY_REFERENCE = new AmperElementType("CONTEXTUAL_PROPERTY_REFERENCE");
  IElementType CONTEXTUAL_STATEMENT = new AmperElementType("CONTEXTUAL_STATEMENT");
  IElementType CONTEXT_BLOCK = new AmperElementType("CONTEXT_BLOCK");
  IElementType CONTEXT_NAME = new AmperElementType("CONTEXT_NAME");
  IElementType INVOCATION_ELEMENT = new AmperElementType("INVOCATION_ELEMENT");
  IElementType INVOCATION_EXPRESSION = new AmperElementType("INVOCATION_EXPRESSION");
  IElementType LITERAL = new AmperElementType("LITERAL");
  IElementType NULL_LITERAL = new AmperElementType("NULL_LITERAL");
  IElementType NUMBER_LITERAL = new AmperElementType("NUMBER_LITERAL");
  IElementType OBJECT = new AmperElementType("OBJECT");
  IElementType OBJECT_ELEMENT = new AmperElementType("OBJECT_ELEMENT");
  IElementType PROPERTY = new AmperElementType("PROPERTY");
  IElementType REFERENCE_EXPRESSION = new AmperElementType("REFERENCE_EXPRESSION");
  IElementType STRING_LITERAL = new AmperElementType("STRING_LITERAL");
  IElementType VALUE = new AmperElementType("VALUE");

  IElementType AT = new AmperTokenType("@");
  IElementType BLOCK_COMMENT = new AmperTokenType("BLOCK_COMMENT");
  IElementType COLON = new AmperTokenType(":");
  IElementType COMMA = new AmperTokenType(",");
  IElementType DOT = new AmperTokenType(".");
  IElementType DOUBLE_QUOTED_STRING = new AmperTokenType("DOUBLE_QUOTED_STRING");
  IElementType EQ = new AmperTokenType("=");
  IElementType FALSE = new AmperTokenType("false");
  IElementType IDENTIFIER = new AmperTokenType("IDENTIFIER");
  IElementType LINE_COMMENT = new AmperTokenType("LINE_COMMENT");
  IElementType L_BRACKET = new AmperTokenType("[");
  IElementType L_CURLY = new AmperTokenType("{");
  IElementType L_PAREN = new AmperTokenType("(");
  IElementType NEGAT = new AmperTokenType("!@");
  IElementType NULL = new AmperTokenType("null");
  IElementType NUMBER = new AmperTokenType("NUMBER");
  IElementType R_BRACKET = new AmperTokenType("]");
  IElementType R_CURLY = new AmperTokenType("}");
  IElementType R_PAREN = new AmperTokenType(")");
  IElementType SHARP = new AmperTokenType("#");
  IElementType SINGLE_QUOTED_STRING = new AmperTokenType("SINGLE_QUOTED_STRING");
  IElementType TRUE = new AmperTokenType("true");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == BOOLEAN_LITERAL) {
        return new AmperBooleanLiteralImpl(node);
      }
      else if (type == CONSTRUCTOR_REFERENCE) {
        return new AmperConstructorReferenceImpl(node);
      }
      else if (type == CONTEXTUAL_PROPERTY_REFERENCE) {
        return new AmperContextualPropertyReferenceImpl(node);
      }
      else if (type == CONTEXTUAL_STATEMENT) {
        return new AmperContextualStatementImpl(node);
      }
      else if (type == CONTEXT_BLOCK) {
        return new AmperContextBlockImpl(node);
      }
      else if (type == CONTEXT_NAME) {
        return new AmperContextNameImpl(node);
      }
      else if (type == INVOCATION_ELEMENT) {
        return new AmperInvocationElementImpl(node);
      }
      else if (type == INVOCATION_EXPRESSION) {
        return new AmperInvocationExpressionImpl(node);
      }
      else if (type == NULL_LITERAL) {
        return new AmperNullLiteralImpl(node);
      }
      else if (type == NUMBER_LITERAL) {
        return new AmperNumberLiteralImpl(node);
      }
      else if (type == OBJECT) {
        return new AmperObjectImpl(node);
      }
      else if (type == PROPERTY) {
        return new AmperPropertyImpl(node);
      }
      else if (type == REFERENCE_EXPRESSION) {
        return new AmperReferenceExpressionImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new AmperStringLiteralImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
