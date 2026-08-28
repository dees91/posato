package app.posato.quality

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.EQ
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.nextCodeLeaf
import com.pinterest.ktlint.rule.engine.core.api.nextLeaf
import com.pinterest.ktlint.rule.engine.core.api.parent
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

internal const val RIGHT_HAND_SIDE_ERROR = "The right-hand side should start on the same line as '='"

internal class RightHandSideOnAssignmentLineRule :
    Rule(
        ruleId = RuleId("$POSATO_RULE_SET_ID:rhs-on-assignment-line"),
        about = About(maintainer = "Posato"),
        usesEditorConfigProperties = setOf(MAX_LINE_LENGTH_PROPERTY),
    ),
    RuleAutocorrectApproveHandler {
    private var maxLineLength = Int.MAX_VALUE

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        maxLineLength = editorConfig[MAX_LINE_LENGTH_PROPERTY]
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType == EQ && node.hasUncommentedLineBreakBeforeRightHandSide() && node.rightHandSideFirstLineFits()) {
            emit(node.startOffset, RIGHT_HAND_SIDE_ERROR, false)
        }
    }

    private fun ASTNode.hasUncommentedLineBreakBeforeRightHandSide(): Boolean {
        var leaf = nextLeaf
        var hasLineBreak = false

        while (leaf != null && !leaf.isCode) {
            if (leaf.isPartOfComment20) {
                return false
            }
            hasLineBreak = hasLineBreak || leaf.isWhiteSpaceWithNewline20
            leaf = leaf.nextLeaf
        }

        return hasLineBreak
    }

    private fun ASTNode.rightHandSideFirstLineFits(): Boolean {
        val rightHandSide = nextCodeLeaf?.takeUnless { it.isMultilineRawStringStart() } ?: return false
        val source = rootText()
        val lineStart = source.lastIndexOf('\n', startOffset - 1) + 1
        val assignmentPrefixLength = startOffset + textLength - lineStart
        val rightHandSideFirstLineLength = source
            .substring(rightHandSide.startOffset)
            .substringBefore('\n')
            .trimEnd()
            .length
        return assignmentPrefixLength + 1 + rightHandSideFirstLineLength <= maxLineLength
    }

    private fun ASTNode.isMultilineRawStringStart(): Boolean = text == "\"\"\"" && parent?.text?.contains('\n') == true

    private fun ASTNode.rootText(): String {
        var root = this
        while (root.parent != null) {
            root = checkNotNull(root.parent)
        }
        return root.text
    }
}
