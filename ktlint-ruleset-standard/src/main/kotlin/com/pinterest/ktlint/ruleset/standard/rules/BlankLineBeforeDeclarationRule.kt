package com.pinterest.ktlint.ruleset.standard.rules

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_INITIALIZER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.EQ
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OBJECT_DECLARATION
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OBJECT_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY_ACCESSOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RETURN_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.ElementType.VALUE_ARGUMENT
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHEN
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.SinceKtlint
import com.pinterest.ktlint.rule.engine.core.api.SinceKtlint.Status.EXPERIMENTAL
import com.pinterest.ktlint.rule.engine.core.api.SinceKtlint.Status.STABLE
import com.pinterest.ktlint.rule.engine.core.api.children20
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isDeclaration20
import com.pinterest.ktlint.rule.engine.core.api.nextCodeSibling20
import com.pinterest.ktlint.rule.engine.core.api.parent
import com.pinterest.ktlint.rule.engine.core.api.prevCodeSibling20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import com.pinterest.ktlint.ruleset.standard.StandardRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Insert a blank line before declarations. No blank line is inserted before between a class or method signature and the first declaration
 * in the class or method respectively. Also, no blank lines are inserted between consecutive properties.
 */
@SinceKtlint("0.50", EXPERIMENTAL)
@SinceKtlint("1.0", STABLE)
public class BlankLineBeforeDeclarationRule :
    StandardRule("blank-line-before-declaration"),
    Rule.OfficialCodeStyle {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        when (node.elementType) {
            CLASS,
            CLASS_INITIALIZER,
            FUN,
            OBJECT_DECLARATION,
            PROPERTY,
            PROPERTY_ACCESSOR,
            -> {
                visitDeclaration(node, emit)
            }
        }
    }

    private fun visitDeclaration(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.isFirstCodeSiblingInClassBody()) {
            // No blank line between class signature and first declaration in class body:
            //   class Foo {
            //      fun bar() {}
            //   }
            //   class Foo {
            //      class bar
            //   }
            return
        }

        if (node.isFirstCodeSiblingInBlock()) {
            // No blank line between opening brace of block and first code sibling in class body:
            //   fun foo() {
            //      class Bar
            //   }
            //   val foo = {
            //      fun bar() {}
            //   }
            return
        }

        if (node.isFirstCodeSiblingInBodyOfFunctionLiteral()) {
            // No blank line between opening brace of function literal and declaration as first code sibling:
            //   val foo = {
            //      fun bar() {}
            //   }
            //   val foo = { _ ->
            //      fun bar() {}
            //   }
            return
        }

        if (node.isConsecutiveProperty()) {
            // Allow consecutive properties:
            //   val foo = "foo"
            //   val bar = "bar"
            return
        }

        if (node.isLocalProperty()) {
            // Allow:
            //   fun foo() {
            //       bar()
            //       val foobar = "foobar"
            //   }
            return
        }

        if (node.elementType == PROPERTY && node.parent?.elementType == WHEN) {
            // Allow:
            //   when(val foo = foo()) {
            //       ...
            //   }
            return
        }

        if (node.elementType == FUN &&
            (node.prevCodeSibling20?.elementType == EQ || node.prevCodeSibling20?.elementType == RETURN_KEYWORD)
        ) {
            // Allow:
            //   val foo =
            //       fun(): String {
            //           return "foo"
            //       }
            return
        }

        if (node.elementType == FUN && node.parent?.elementType == VALUE_ARGUMENT) {
            // Allow:
            //   val foo1 = foo2(fun() = 42)
            return
        }

        if (node.elementType == OBJECT_DECLARATION && node.parent?.elementType == OBJECT_LITERAL) {
            // Allow:
            //   fun foo() =
            //      object : Foo() {
            //          // some declarations
            //      }
            return
        }

        node
            .takeIf { it.isDeclaration20 }
            ?.takeUnless { it.prevLeaf.isBlankLine() }
            ?.let { insertBeforeNode ->
                emit(insertBeforeNode.startOffset, "Expected a blank line for this declaration", true)
                    .ifAutocorrectAllowed {
                        insertBeforeNode.upsertWhitespaceBeforeMe("\n".plus(node.indent20))
                    }
            }
    }

    private fun ASTNode?.isBlankLine() = this == null || text.startsWith("\n\n")

    private fun ASTNode.isFirstCodeSiblingInClassBody() =
        this ==
            parent
                ?.takeIf { it.elementType == CLASS_BODY }
                ?.findChildByType(LBRACE)
                ?.nextCodeSibling20

    private fun ASTNode.isFirstCodeSiblingInBlock() =
        this ==
            parent
                ?.takeIf { it.elementType == BLOCK }
                ?.findChildByType(LBRACE)
                ?.nextCodeSibling20

    private fun ASTNode.isFirstCodeSiblingInBodyOfFunctionLiteral() =
        this ==
            parent
                ?.takeIf { it.elementType == BLOCK && it.parent?.elementType == FUNCTION_LITERAL }
                ?.parent
                ?.takeIf { it.elementType == FUNCTION_LITERAL }
                ?.findChildByType(BLOCK)
                ?.children20
                ?.firstOrNull { isCode }

    private fun ASTNode.isConsecutiveProperty() =
        takeIf { it.propertyRelated() }
            ?.prevCodeSibling20
            ?.let { it.propertyRelated() || it.parent!!.propertyRelated() }
            ?: false

    private fun ASTNode.isLocalProperty() =
        takeIf { it.propertyRelated() }
            ?.parent
            ?.let { it.elementType == BLOCK }
            ?: false

    private fun ASTNode.propertyRelated() = elementType == PROPERTY || elementType == PROPERTY_ACCESSOR
}

public val BLANK_LINE_BEFORE_DECLARATION_RULE_ID: RuleId = BlankLineBeforeDeclarationRule().ruleId
