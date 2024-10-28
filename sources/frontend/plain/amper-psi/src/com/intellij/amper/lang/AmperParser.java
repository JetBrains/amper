// This is a generated file. Not intended for manual editing.
package com.intellij.amper.lang;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.amper.lang.AmperElementTypes.*;
import static com.intellij.amper.lang.AmperParserUtil.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AmperParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return amper(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(CONTEXTUAL_ELEMENT, CONTEXTUAL_STATEMENT, CONTEXT_BLOCK, INVOCATION_ELEMENT,
      OBJECT_ELEMENT, PROPERTY, VARIABLE_DECLARATION),
    create_token_set_(BOOLEAN_LITERAL, CONTEXTUAL_PROPERTY_REFERENCE, INVOCATION_EXPRESSION, LITERAL,
      NULL_LITERAL, NUMBER_LITERAL, OBJECT, REFERENCE_EXPRESSION,
      STRING_LITERAL, VALUE),
  };

  /* ********************************************************** */
  // object_element*
  static boolean amper(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "amper")) return false;
    while (true) {
      int c = current_position_(b);
      if (!object_element(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "amper", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // value
  static boolean argument(PsiBuilder b, int l) {
    return value(b, l + 1);
  }

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean boolean_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "boolean_literal")) return false;
    if (!nextTokenIs(b, "<boolean literal>", FALSE, TRUE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BOOLEAN_LITERAL, "<boolean literal>");
    r = consumeToken(b, TRUE);
    if (!r) r = consumeToken(b, FALSE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // invocation_expression | reference_expression | string_literal
  public static boolean constructor_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONSTRUCTOR_REFERENCE, "<constructor reference>");
    r = invocation_expression(b, l + 1);
    if (!r) r = reference_expression(b, l + 1);
    if (!r) r = string_literal(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // context_names '{' (object_element (','|&'}')?)* '}'
  public static boolean context_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block")) return false;
    if (!nextTokenIs(b, "<context block>", AT, NEGAT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONTEXT_BLOCK, "<context block>");
    r = context_names(b, l + 1);
    r = r && consumeToken(b, L_CURLY);
    p = r; // pin = 2
    r = r && report_error_(b, context_block_2(b, l + 1));
    r = p && consumeToken(b, R_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (object_element (','|&'}')?)*
  private static boolean context_block_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!context_block_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "context_block_2", c)) break;
    }
    return true;
  }

  // object_element (','|&'}')?
  private static boolean context_block_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = object_element(b, l + 1);
    r = r && context_block_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (','|&'}')?
  private static boolean context_block_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block_2_0_1")) return false;
    context_block_2_0_1_0(b, l + 1);
    return true;
  }

  // ','|&'}'
  private static boolean context_block_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block_2_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    if (!r) r = context_block_2_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean context_block_2_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_block_2_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, R_CURLY);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ('@'|'!@') IDENTIFIER
  public static boolean context_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_name")) return false;
    if (!nextTokenIs(b, "<context name>", AT, NEGAT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONTEXT_NAME, "<context name>");
    r = context_name_0(b, l + 1);
    p = r; // pin = 1
    r = r && consumeToken(b, IDENTIFIER);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '@'|'!@'
  private static boolean context_name_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_name_0")) return false;
    boolean r;
    r = consumeToken(b, AT);
    if (!r) r = consumeToken(b, NEGAT);
    return r;
  }

  /* ********************************************************** */
  // (context_name (','|&'}')?)+
  static boolean context_names(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_names")) return false;
    if (!nextTokenIs(b, "", AT, NEGAT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = context_names_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!context_names_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "context_names", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // context_name (','|&'}')?
  private static boolean context_names_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_names_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = context_name(b, l + 1);
    p = r; // pin = 1
    r = r && context_names_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (','|&'}')?
  private static boolean context_names_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_names_0_1")) return false;
    context_names_0_1_0(b, l + 1);
    return true;
  }

  // ','|&'}'
  private static boolean context_names_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_names_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    if (!r) r = context_names_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean context_names_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "context_names_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, R_CURLY);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // context_block | contextual_statement
  public static boolean contextual_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextual_element")) return false;
    if (!nextTokenIs(b, "<contextual element>", AT, NEGAT)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CONTEXTUAL_ELEMENT, "<contextual element>");
    r = context_block(b, l + 1);
    if (!r) r = contextual_statement(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '.' reference_expression
  public static boolean contextual_property_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextual_property_reference")) return false;
    if (!nextTokenIs(b, DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && reference_expression(b, l + 1);
    exit_section_(b, m, CONTEXTUAL_PROPERTY_REFERENCE, r);
    return r;
  }

  /* ********************************************************** */
  // context_names (invocation_element | variable_declaration | property)
  public static boolean contextual_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextual_statement")) return false;
    if (!nextTokenIs(b, "<contextual statement>", AT, NEGAT)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONTEXTUAL_STATEMENT, "<contextual statement>");
    r = context_names(b, l + 1);
    r = r && contextual_statement_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // invocation_element | variable_declaration | property
  private static boolean contextual_statement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "contextual_statement_1")) return false;
    boolean r;
    r = invocation_element(b, l + 1);
    if (!r) r = variable_declaration(b, l + 1);
    if (!r) r = property(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // invocation_expression
  public static boolean invocation_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "invocation_element")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = invocation_expression(b, l + 1);
    exit_section_(b, m, INVOCATION_ELEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // reference_expression '(' (argument (',')?)* ')'
  public static boolean invocation_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "invocation_expression")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, INVOCATION_EXPRESSION, null);
    r = reference_expression(b, l + 1);
    r = r && consumeToken(b, L_PAREN);
    p = r; // pin = 2
    r = r && report_error_(b, invocation_expression_2(b, l + 1));
    r = p && consumeToken(b, R_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (argument (',')?)*
  private static boolean invocation_expression_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "invocation_expression_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!invocation_expression_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "invocation_expression_2", c)) break;
    }
    return true;
  }

  // argument (',')?
  private static boolean invocation_expression_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "invocation_expression_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = argument(b, l + 1);
    r = r && invocation_expression_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',')?
  private static boolean invocation_expression_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "invocation_expression_2_0_1")) return false;
    consumeToken(b, COMMA);
    return true;
  }

  /* ********************************************************** */
  // string_literal | number_literal | boolean_literal | null_literal
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL, "<literal>");
    r = string_literal(b, l + 1);
    if (!r) r = number_literal(b, l + 1);
    if (!r) r = boolean_literal(b, l + 1);
    if (!r) r = null_literal(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // !('}'|'#'|'@'|'!@'|','|value)
  static boolean not_brace_or_next_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_brace_or_next_value")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !not_brace_or_next_value_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '}'|'#'|'@'|'!@'|','|value
  private static boolean not_brace_or_next_value_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_brace_or_next_value_0")) return false;
    boolean r;
    r = consumeToken(b, R_CURLY);
    if (!r) r = consumeToken(b, SHARP);
    if (!r) r = consumeToken(b, AT);
    if (!r) r = consumeToken(b, NEGAT);
    if (!r) r = consumeToken(b, COMMA);
    if (!r) r = value(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // NULL
  public static boolean null_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "null_literal")) return false;
    if (!nextTokenIs(b, NULL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NULL);
    exit_section_(b, m, NULL_LITERAL, r);
    return r;
  }

  /* ********************************************************** */
  // NUMBER
  public static boolean number_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "number_literal")) return false;
    if (!nextTokenIs(b, NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NUMBER);
    exit_section_(b, m, NUMBER_LITERAL, r);
    return r;
  }

  /* ********************************************************** */
  // constructor_reference? '{' (object_element (','|&'}')?)* '}'
  public static boolean object(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OBJECT, "<object>");
    r = object_0(b, l + 1);
    r = r && consumeToken(b, L_CURLY);
    p = r; // pin = 2
    r = r && report_error_(b, object_2(b, l + 1));
    r = p && consumeToken(b, R_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // constructor_reference?
  private static boolean object_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_0")) return false;
    constructor_reference(b, l + 1);
    return true;
  }

  // (object_element (','|&'}')?)*
  private static boolean object_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!object_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "object_2", c)) break;
    }
    return true;
  }

  // object_element (','|&'}')?
  private static boolean object_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = object_element(b, l + 1);
    r = r && object_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (','|&'}')?
  private static boolean object_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_2_0_1")) return false;
    object_2_0_1_0(b, l + 1);
    return true;
  }

  // ','|&'}'
  private static boolean object_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_2_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    if (!r) r = object_2_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean object_2_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_2_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, R_CURLY);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // contextual_element | invocation_element | variable_declaration | property
  public static boolean object_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "object_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, OBJECT_ELEMENT, "<object element>");
    r = contextual_element(b, l + 1);
    if (!r) r = invocation_element(b, l + 1);
    if (!r) r = variable_declaration(b, l + 1);
    if (!r) r = property(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // property_name (('=' property_value) | (&('{') object))?
  public static boolean property(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PROPERTY, "<property>");
    r = property_name(b, l + 1);
    r = r && property_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (('=' property_value) | (&('{') object))?
  private static boolean property_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1")) return false;
    property_1_0(b, l + 1);
    return true;
  }

  // ('=' property_value) | (&('{') object)
  private static boolean property_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_1_0_0(b, l + 1);
    if (!r) r = property_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '=' property_value
  private static boolean property_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EQ);
    r = r && property_value(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &('{') object
  private static boolean property_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_1_0_1_0(b, l + 1);
    r = r && object(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &('{')
  private static boolean property_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, L_CURLY);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // literal | reference_expression
  static boolean property_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_name")) return false;
    boolean r;
    r = literal(b, l + 1);
    if (!r) r = reference_expression(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // value
  static boolean property_value(PsiBuilder b, int l) {
    return value(b, l + 1);
  }

  /* ********************************************************** */
  // (IDENTIFIER)
  public static boolean qualified_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_expression")) return false;
    if (!nextTokenIsFast(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, REFERENCE_EXPRESSION, null);
    r = consumeTokenFast(b, IDENTIFIER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // reference_literal ('.' qualified_expression)*
  public static boolean reference_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_expression")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, REFERENCE_EXPRESSION, null);
    r = reference_literal(b, l + 1);
    r = r && reference_expression_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ('.' qualified_expression)*
  private static boolean reference_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_expression_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!reference_expression_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "reference_expression_1", c)) break;
    }
    return true;
  }

  // '.' qualified_expression
  private static boolean reference_expression_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_expression_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && qualified_expression(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean reference_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reference_literal")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, REFERENCE_EXPRESSION, r);
    return r;
  }

  /* ********************************************************** */
  // SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING
  public static boolean string_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_literal")) return false;
    if (!nextTokenIs(b, "<string literal>", DOUBLE_QUOTED_STRING, SINGLE_QUOTED_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING_LITERAL, "<string literal>");
    r = consumeToken(b, SINGLE_QUOTED_STRING);
    if (!r) r = consumeToken(b, DOUBLE_QUOTED_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // object | literal | invocation_expression | contextual_property_reference | reference_expression
  public static boolean value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, VALUE, "<value>");
    r = object(b, l + 1);
    if (!r) r = literal(b, l + 1);
    if (!r) r = invocation_expression(b, l + 1);
    if (!r) r = contextual_property_reference(b, l + 1);
    if (!r) r = reference_expression(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // VAL_KEYWORD IDENTIFIER '=' property_value
  public static boolean variable_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_declaration")) return false;
    if (!nextTokenIs(b, VAL_KEYWORD)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, VARIABLE_DECLARATION, null);
    r = consumeTokens(b, 1, VAL_KEYWORD, IDENTIFIER, EQ);
    p = r; // pin = 1
    r = r && property_value(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

}
