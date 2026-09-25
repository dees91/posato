package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RepoLayout
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CandidateTargetingTest {
    @Test
    fun `given no installed candidate when resolving the desktop application then the staged package is driven`() {
        val layout = layout()

        assertEquals(layout.stagedDesktopApplication, layout.desktopApplication)
    }

    @Test
    fun `given an installed candidate marker when resolving the desktop application then the installed bundle is driven`() {
        val layout = layout()
        layout.installedApplicationMarker.parent.createDirectories()
        layout.installedApplicationMarker.writeText("/Applications/Posato.app\n")

        assertEquals("/Applications/Posato.app", layout.desktopApplication.toString())
    }

    @Test
    fun `codesign details classify Developer ID, development, and ad-hoc signatures`() {
        val developerId = "Authority=Developer ID Application: Example (TEAM123456)\nTeamIdentifier=TEAM123456\n"
        val development = "Authority=Apple Development: Example (ABCDE12345)\nTeamIdentifier=TEAM123456\n"
        val adhoc = "Signature=adhoc\nTeamIdentifier=not set\n"

        assertEquals("developer-id", signingModeOf(developerId))
        assertEquals("development", signingModeOf(development))
        assertEquals("adhoc", signingModeOf(adhoc))
    }

    @Test
    fun `exactly one running instance is adopted`() {
        assertEquals(42L, singleInstance(listOf(42L)))
    }

    @Test
    fun `no running instance leaves nothing to adopt`() {
        assertEquals(ErrorCode.APP_NOT_RUNNING, assertFailsWith<ControlException> { singleInstance(emptyList()) }.code)
    }

    @Test
    fun `several running instances are never adopted by guess`() {
        assertEquals(ErrorCode.ALREADY_RUNNING, assertFailsWith<ControlException> { singleInstance(listOf(1L, 2L)) }.code)
    }

    private fun layout(): RepoLayout {
        val root = createTempDirectory("posato-control-candidate")
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"Posato\"\n")
        return RepoLayout(root)
    }
}
