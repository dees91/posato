package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CandidateInstallTest {
    private val installed = "/Applications/Posato.app"

    @Test
    fun `given stale and development registrations when filtering then only Posato bundles outside the candidate remain`() {
        val registrations = listOf(
            "/Applications/Posato.app",
            "/Applications/Posato.app/Contents/Helpers/PosatoMacOSHelper.app",
            "/Applications/Safari.app",
            "/Users/guest/posato-run/desktopApp/build/compose/binaries/main/development-package/Posato.app",
            "/Volumes/Posato/Posato.app/Contents/Helpers/PosatoMacOSSync.app",
            "/private/var/folders/x/T/AppTranslocation/ID/d/Posato.app",
        )

        assertEquals(
            listOf(
                "/Users/guest/posato-run/desktopApp/build/compose/binaries/main/development-package/Posato.app",
                "/Volumes/Posato/Posato.app/Contents/Helpers/PosatoMacOSSync.app",
                "/private/var/folders/x/T/AppTranslocation/ID/d/Posato.app",
            ),
            foreignRegistrations(registrations, installed),
        )
    }

    @Test
    fun `a sibling bundle whose path only starts like the candidate is still foreign`() {
        val sibling = "/Applications/Posato.app copy/Posato.app"

        assertEquals(listOf(sibling), foreignRegistrations(listOf(sibling), installed))
    }

    @Test
    fun `given only the candidate and its helpers when checking then the single-bundle check holds`() {
        val registrations = listOf(installed, "$installed/Contents/Helpers/PosatoMacOSSync.app", "/Applications/Safari.app")

        assertNull(singleBundleProblem(registrations, installed))
    }

    @Test
    fun `given an empty or unreadable dump when checking then the single-bundle check fails`() {
        assertNotNull(singleBundleProblem(emptyList(), installed))
    }

    @Test
    fun `given another Posato bundle when checking then the single-bundle check fails`() {
        assertNotNull(singleBundleProblem(listOf(installed, "/Volumes/Posato/Posato.app"), installed))
    }

    @Test
    fun `mount points are read only for the candidate image`() {
        val info =
            """
            {"images":[
              {"image-path":"/Users/guest/posato-run/candidates/A.dmg","system-entities":[{"dev-entry":"/dev/disk4"},{"dev-entry":"/dev/disk4s1","mount-point":"/Volumes/Posato"}]},
              {"image-path":"/Users/guest/other.dmg","system-entities":[{"mount-point":"/Volumes/Other"}]}
            ]}
            """.trimIndent()

        assertEquals(listOf("/Volumes/Posato"), mountPointsOf(info, "/Users/guest/posato-run/candidates/A.dmg"))
    }

    @Test
    fun `no attached images yield no mount points`() {
        assertEquals(emptyList(), mountPointsOf("{}", "/Users/guest/posato-run/candidates/A.dmg"))
    }

    @Test
    fun `a candidate file name must be safe to use unquoted in guest scripts`() {
        assertEquals("Posato-1.1.0-15.dmg", candidateFileName(Path.of("/tmp/Posato-1.1.0-15.dmg")))

        val failure = assertFailsWith<ControlException> { candidateFileName(Path.of("/tmp/Posato \$(id).dmg")) }

        assertEquals(ErrorCode.USAGE, failure.code)
    }

    @Test
    fun `signing details are read from the first matching codesign line`() {
        val details = "Executable=/Applications/Posato.app/Contents/MacOS/Posato\n" +
            "Authority=Developer ID Application: Example (TEAM123456)\nAuthority=Developer ID Certification Authority\nTeamIdentifier=TEAM123456\n"

        assertEquals("Developer ID Application: Example (TEAM123456)", signingDetail(details, "Authority="))
        assertEquals("TEAM123456", signingDetail(details, "TeamIdentifier="))
        assertNull(signingDetail(details, "Sealed Resources="))
    }
}
