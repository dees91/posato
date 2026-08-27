package app.posato.quality

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import kotlin.test.Test
import kotlin.test.assertEquals

class RightHandSideOnAssignmentLineRuleTest {
    @Test
    fun `multiline declaration starts its value after equals when it fits`() {
        val errors = lint(
            """
            val invalidInputs =
                listOf(
                    "example",
                )
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(1, errors.single().line)
        assertEquals("The right-hand side should start on the same line as '='", errors.single().detail)
    }

    @Test
    fun `named argument starts its value after equals when it fits`() {
        val errors = lint(
            """
            fun content(modifier: Any) = Unit

            fun render() {
                content(
                    modifier =
                        Modifier.padding(),
                )
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(5, errors.single().line)
    }

    @Test
    fun `expression body starts after equals when it fits`() {
        val errors = lint(
            """
            fun domains() =
                listOf(
                    "example.com",
                )
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(1, errors.single().line)
    }

    @Test
    fun `default value starts after equals when it fits`() {
        val errors = lint(
            """
            fun domains(
                initial: List<String> =
                    listOf(
                        "example.com",
                ),
            ) = initial
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(2, errors.single().line)
    }

    @Test
    fun `assignment starts its value after equals when it fits`() {
        val errors = lint(
            """
            fun replace() {
                var domains = emptyList<String>()
                domains =
                    listOf(
                        "example.com",
                    )
            }
            """.trimIndent(),
        )

        assertEquals(1, errors.size)
        assertEquals(3, errors.single().line)
    }

    @Test
    fun `comment between equals and value is accepted`() {
        val errors = lint(
            """
            val domains =
                // Keep the rationale adjacent to the value.
                listOf("example.com")
            """.trimIndent(),
        )

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `multiline raw string uses the standard ktlint line break`() {
        val errors = lint("val message =\n    \"\"\"\n    example\n    \"\"\".trimIndent()")

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `single-line raw string starts after equals when it fits`() {
        val errors = lint("val pattern =\n    \"\"\"[a-z]+\"\"\"")

        assertEquals(1, errors.size)
        assertEquals(1, errors.single().line)
    }

    @Test
    fun `value that would exceed the configured line length is accepted below equals`() {
        val errors = lint(
            code =
                """
                val domains =
                    createAnIntentionallyLongDomainCollection(
                        "example.com",
                    )
                """.trimIndent(),
            maxLineLength = 40,
        )

        assertEquals(emptyList(), errors)
    }

    @Test
    fun `multiline value starting after equals is accepted`() {
        val errors = lint(
            """
            val domains = listOf(
                "example.com",
            )
            """.trimIndent(),
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
