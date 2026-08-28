package app.posato.quality

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ARROW
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHEN_ENTRY
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.parent
import com.pinterest.ktlint.rule.engine.core.api.prevCodeLeaf
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

internal const val WHEN_ENTRY_ARROW_ERROR = "The 'when' entry arrow should be on the same line as its final condition"

internal class WhenEntryArrowOnConditionLineRule :
    Rule(
        ruleId = RuleId("$POSATO_RULE_SET_ID:when-entry-arrow-on-condition-line"),
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
        if (node.elementType == ARROW && node.parent?.elementType == WHEN_ENTRY) {
            if (node.hasUncommentedLineBreakAfterCondition() && node.arrowFitsOnConditionLine()) {
                emit(node.startOffset, WHEN_ENTRY_ARROW_ERROR, false)
            }
        }
    }

    private fun ASTNode.hasUncommentedLineBreakAfterCondition(): Boolean {
        var leaf = prevLeaf
        var hasLineBreak = false

        while (leaf != null && !leaf.isCode) {
            if (leaf.isPartOfComment20) {
                return false
            }
            hasLineBreak = hasLineBreak || leaf.isWhiteSpaceWithNewline20
            leaf = leaf.prevLeaf
        }

        return hasLineBreak
    }

    private fun ASTNode.arrowFitsOnConditionLine(): Boolean {
        val condition = prevCodeLeaf ?: return false
        val source = rootText()
        val lineStart = source.lastIndexOf('\n', condition.startOffset - 1) + 1
        val conditionLineLength = condition.startOffset + condition.textLength - lineStart
        return conditionLineLength + 1 + textLength <= maxLineLength
    }

    private fun ASTNode.rootText(): String {
        var root = this
        while (root.parent != null) {
            root = checkNotNull(root.parent)
        }
        return root.text
    }
}
