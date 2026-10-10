package app.posato.control.cli

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every completed-change review of release 1.4 found evidence that did not name the head or cited runs that had no
 * directory. The check reads the PR's `Verified` lines and the product paths changed since the verified commit, so
 * each review starts from evidence that holds; a reviewer reading comments by hand is what missed them before.
 */
class PrEvidenceTest {
    @Test
    fun `given Verified comments when parsing then each names its commit and cited runs`() {
        val comments = listOf(
            "Looks good.",
            "Verified 2dd1fac3 on Tart macOS 26 (notarized 9101/9102): Cmd-Q asks -> pass, run 20261009-220816-023c, " +
                "run 20261009-220633-cc9b",
            "Verified c8d9d5b on desktop (vm peer): loaded probe -> ready, run 20261009-221200-ab12",
        )
        val parsed = parseVerifiedLines(comments)
        assertEquals(listOf("2dd1fac3", "c8d9d5b"), parsed.map { it.commit })
        assertEquals(listOf("20261009-220816-023c", "20261009-220633-cc9b"), parsed.first().runIds)
    }

    @Test
    fun `given changed paths when judging the verified commit then only product paths count`() {
        val changed = listOf(
            "docs/tasks/executions/x.md",
            "shared/src/commonMain/kotlin/X.kt",
            "desktopApp/build.gradle.kts",
            "tools/posato-control/README.md",
            ".agents/skills/verify-posato/SKILL.md",
            "macosHelper/Sources/A.swift",
        )
        assertEquals(
            listOf("shared/src/commonMain/kotlin/X.kt", "desktopApp/build.gradle.kts", "macosHelper/Sources/A.swift"),
            productChanges(changed),
        )
    }
}
