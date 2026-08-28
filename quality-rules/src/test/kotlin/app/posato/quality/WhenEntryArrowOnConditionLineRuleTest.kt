package app.posato.quality

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import kotlin.test.Test
import kotlin.test.assertEquals

class WhenEntryArrowOnConditionLineRuleTest {
    @Test
    fun `multi-condition entry keeps arrow on final condition line when it fits`() {
        val errors = lint(
            """
            fun label(value: String?) = when (value) {
                "one",
                "two",
                -> "number"

                else -> "other"
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(4, errors.single().line)
        assertEquals(WHEN_ENTRY_ARROW_ERROR, errors.single().detail)
    }

    @Test
    fun `nullable entry keeps arrow on final condition line when it fits`() {
        val errors = lint(
            """
            fun label(value: String?) = when (value) {
                "one",
                null,
                -> "empty"

                else -> "other"
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(4, errors.single().line)
    }

    @Test
    fun `arrow on final condition line is accepted`() {
        val errors = lint(
            """
            fun label(value: String?) = when (value) {
                "one",
                null -> "empty"

                else -> "other"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `comment between final condition and arrow is accepted`() {
        val errors = lint(
            """
            fun label(value: String?) = when (value) {
                "one",
                null,
                // The fallback is intentionally grouped.
                -> "empty"

                else -> "other"
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `condition that would exceed configured line length is accepted above arrow`() {
        val errors = lint(
            code =
                """
                fun label(value: String?) = when (value) {
                    "an-intentionally-long-final-condition",
                    -> "long"

                    else -> "other"
                }
                """.trimIndent(),
            maxLineLength = 40,
        )

        assertEquals(emptyList(), errors)
    }

    private fun lint(
        code: String,
        maxLineLength: Int = 150,
    ): List<LintError> = buildList {
        KtLintRuleEngine(
            ruleProviders = PosatoRuleSetProvider().getRuleProviders(),
            editorConfigOverride = EditorConfigOverride.from(MAX_LINE_LENGTH_PROPERTY to maxLineLength),
        ).lint(Code.fromSnippet(code), ::add)
    }
}
