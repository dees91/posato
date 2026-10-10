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
        val parsed = parseVerifiedLines(comments, trustedAuthors = setOf("dees91"))
        assertEquals(listOf("2dd1fac3"), parsed.map { it.commit })
        assertEquals(listOf("20261009-220816-023c", "ac01-9103"), parsed.single().runIds)
    }
}
