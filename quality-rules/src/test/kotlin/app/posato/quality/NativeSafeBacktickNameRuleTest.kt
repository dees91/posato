package app.posato.quality

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.createRuleExecutionEditorConfigProperty
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeSafeBacktickNameRuleTest {
    @Test
    fun `a test name with a comma is reported`() {
        val errors = lint(
            """
            class Probe {
                fun `given a pause, when it ends`() {}
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(2, errors.single().line)
        assertEquals(NATIVE_SAFE_NAME_ERROR, errors.single().detail)
    }

    @Test
    fun `every character Kotlin Native rejects is reported`() {
        val names = NATIVE_REJECTED_NAME_CHARACTERS.map { character -> "    fun `x${character}y`() {}" }

        val errors = lint("class Probe {\n${names.joinToString("\n")}\n}")

        assertEquals(NATIVE_REJECTED_NAME_CHARACTERS.length, errors.size)
    }

    @Test
    fun `apostrophes and dashes and plain words are accepted`() {
        val errors = lint(
            """
            class Probe {
                fun `given the helper's end when a 1-minute gap passes then it's kept`() {}

                fun plainName() {}
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), errors)
    }

    // The repository's .editorconfig enables this rule only under shared/src, and a snippet resolves outside it.
    private fun lint(code: String): List<LintError> = buildList {
        val rule = NativeSafeBacktickNameRule()
        val enabled = EditorConfigOverride.from(rule.ruleId.createRuleExecutionEditorConfigProperty() to RuleExecution.enabled)
        KtLintRuleEngine(ruleProviders = setOf(RuleProvider { rule }), editorConfigOverride = enabled).lint(Code.fromSnippet(code), ::add)
    }
}
