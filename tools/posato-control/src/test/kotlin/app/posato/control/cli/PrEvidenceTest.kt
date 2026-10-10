package app.posato.control.cli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every completed-change review of release 1.4 found evidence that did not name the head or cited runs that had no
 * directory. The check reads only the `Verified` lines the PR's author or the maintainer posted, as written at the
 * start of a line, and every cited run, also one stored under a label; a quoted or third-party line must not count.
 */
class PrEvidenceTest {
    @Test
    fun `given comments when parsing then only line-start Verified lines by trusted authors count, with every cited run`() {
        val comments = listOf(
            PrComment("dees91", "Looks good."),
            PrComment(
                "dees91",
                "Verified 2dd1fac3 on Tart macOS 26: Cmd-Q asks -> pass, run 20261009-220816-023c, run ac01-9103; " +
                    "a second run the same way passed.\n" +
                    "> Verified 1111111 on desktop: quoted, run quoted-1",
            ),
            PrComment("someone-else", "Verified 2222222 on desktop: drive-by, run other-1"),
            PrComment("dees91", "Earlier: we Verified 3333333 inline, run inline-1"),
        )
        val parsed = parseVerifiedLines(comments, trustedAuthors = setOf("dees91"), runExists = { false })
        assertEquals(listOf("2dd1fac3"), parsed.map { it.commit })
        assertEquals(listOf("20261009-220816-023c", "ac01-9103"), parsed.single().runIds)
    }

    /** A label without a digit passed the gate with no cited run (review of 4fab21d). */
    @Test
    fun `given a label without a digit when its run exists then it is cited, otherwise the line cites nothing`() {
        val comments = listOf(
            PrComment("dees91", "Verified 4444444 on desktop: passed, run smoke"),
            PrComment("dees91", "Verified 5555555 on desktop: passed, run ghost"),
        )
        val parsed = parseVerifiedLines(comments, trustedAuthors = setOf("dees91"), runExists = { it == "smoke" })
        assertEquals(listOf(listOf("smoke"), emptyList()), parsed.map { it.runIds })
    }

    /** Adding only the driver's README after the verified commit failed the gate (review of 4fab21d). */
    @Test
    fun `given documentation inside product and tooling directories when scoping then it is not product code`() {
        val paths = listOf("tools/posato-control/README.md", "shared/notes.md", "tools/posato-control/src/Main.kt")
        assertEquals(listOf("tools/posato-control/src/Main.kt"), productPaths(paths, includeTooling = true))
    }
}
