package app.posato.quality

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IDENTIFIER
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Characters Kotlin/Native rejects in a backtick-quoted name, beyond those the JVM compiler already rejects. A test
 * name with one of them compiles and runs on the JVM, then fails only when the iOS test sources compile, late in
 * `quality` (observed for "," in RELEASE-004; the set was probed against the Kotlin/Native compiler).
 */
internal const val NATIVE_REJECTED_NAME_CHARACTERS = ",(){}\$&~*?#|%@"

internal const val NATIVE_SAFE_NAME_ERROR = "Kotlin/Native rejects this name; remove any of: $NATIVE_REJECTED_NAME_CHARACTERS"

internal class NativeSafeBacktickNameRule :
    Rule(
        ruleId = RuleId("$POSATO_RULE_SET_ID:native-safe-backtick-name"),
        about = About(maintainer = "Posato"),
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType == IDENTIFIER && node.text.isBacktickQuoted() && node.text.any { it in NATIVE_REJECTED_NAME_CHARACTERS }) {
            emit(node.startOffset, NATIVE_SAFE_NAME_ERROR, false)
        }
    }

    private fun String.isBacktickQuoted(): Boolean = length > 2 && startsWith('`') && endsWith('`')
}
