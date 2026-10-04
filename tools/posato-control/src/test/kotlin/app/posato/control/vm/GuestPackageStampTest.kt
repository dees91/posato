package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * A host rebuild reaches a guest only through `vm sync`; a launch that silently ran the previous package verified the
 * wrong build. The stamp written at sync time must change with any staged file and stop such a launch.
 */
class GuestPackageStampTest {
    private fun stagedApp(): Path {
        val root = Files.createTempDirectory("staged").resolve("Posato.app")
        root.resolve("Contents/MacOS").createDirectories()
        root.resolve("Contents/MacOS/Posato").writeText("binary one")
        root.resolve("Contents/Info.plist").writeText("plist")
        return root
    }

    @Test
    fun `the fingerprint stays for an unchanged package and changes with any file`() {
        val app = stagedApp()
        val first = packageFingerprint(app)

        assertEquals(first, packageFingerprint(app))
        app.resolve("Contents/MacOS/Posato").writeText("binary two, rebuilt")
        assertNotEquals(first, packageFingerprint(app))
    }

    @Test
    fun `a launch is refused when the guest holds another package than the staged one`() {
        val failure = assertFailsWith<ControlException> { refuseOutdatedPackage(synced = "a", staged = "b", line = VmLine.PRIMARY) }

        assertEquals(ErrorCode.PACKAGE_OUTDATED, failure.code)
        assertEquals(true, failure.hint?.contains("vm sync --line primary"))
    }

    @Test
    fun `a launch passes when nothing is staged, or the guest holds the staged package`() {
        refuseOutdatedPackage(synced = null, staged = null, line = VmLine.PRIMARY)
        refuseOutdatedPackage(synced = "a", staged = "a", line = VmLine.PRIMARY)
    }

    @Test
    fun `a guest synced before stamps existed counts as outdated once a package is staged`() {
        assertEquals(
            ErrorCode.PACKAGE_OUTDATED,
            assertFailsWith<ControlException> { refuseOutdatedPackage(synced = null, staged = "a", line = VmLine.PEER) }.code,
        )
    }
}
