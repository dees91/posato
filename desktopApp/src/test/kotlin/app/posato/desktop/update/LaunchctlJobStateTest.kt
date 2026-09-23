package app.posato.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchctlJobStateTest {
    @Test
    fun `given the exact not-found answer when parsed then the installer job is absent`() {
        val output = "Bad request.\nCould not find service \"$LABEL\" in domain for user gui: 501\n"

        assertEquals(LaunchctlJobState.ABSENT, parseLaunchctlPrint(LABEL, NOT_FOUND_EXIT, output))
    }

    @Test
    fun `given the system and user domain not-found answers when parsed then the installer job is absent`() {
        val system = "Bad request.\nCould not find service \"$LABEL\" in domain for system\n"
        val user = "Bad request.\nCould not find service \"$LABEL\" in domain for uid: 501\n"

        assertEquals(LaunchctlJobState.ABSENT, parseLaunchctlPrint(LABEL, NOT_FOUND_EXIT, system))
        assertEquals(LaunchctlJobState.ABSENT, parseLaunchctlPrint(LABEL, NOT_FOUND_EXIT, user))
    }

    @Test
    fun `given a not-found answer for another label when parsed then the state is unknown`() {
        val output = "Bad request.\nCould not find service \"other.label\" in domain for system\n"

        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, NOT_FOUND_EXIT, output))
    }

    @Test
    fun `given a not-found text with another exit code when parsed then the state is unknown`() {
        val output = "Bad request.\nCould not find service \"$LABEL\" in domain for system\n"

        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 1, output))
    }

    @Test
    fun `given a running job with a pid when parsed then the installer is running`() {
        assertEquals(LaunchctlJobState.RUNNING, parseLaunchctlPrint(LABEL, 0, printed("running", pid = 52636)))
    }

    @Test
    fun `given a loaded job that is not running and has no pid when parsed then it is not running`() {
        assertEquals(LaunchctlJobState.NOT_RUNNING, parseLaunchctlPrint(LABEL, 0, printed("not running", pid = null)))
    }

    @Test
    fun `given a not running state that still lists a pid when parsed then the state is unknown`() {
        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 0, printed("not running", pid = 7)))
    }

    @Test
    fun `given an unrecognized state or header when parsed then the state is unknown`() {
        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 0, printed("spawn scheduled", pid = null)))
        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 0, printed("running", pid = 1).replace("gui/501/$LABEL", "gui/501/other")))
        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 0, ""))
    }

    @Test
    fun `given two top-level state lines when parsed then the state is unknown`() {
        val doubled = printed("not running", pid = null).replace("\truns = 1\n", "\truns = 1\n\tstate = running\n")

        assertEquals(LaunchctlJobState.UNKNOWN, parseLaunchctlPrint(LABEL, 0, doubled))
    }

    private fun printed(
        state: String,
        pid: Int?,
    ): String {
        return buildString {
            append("gui/501/$LABEL = {\n")
            append("\tactive count = 1\n")
            append("\ttype = LaunchAgent\n")
            append("\tstate = $state\n")
            append("\n")
            append("\tprogram = /Applications/Posato.app/Contents/Frameworks/Sparkle.framework/Versions/B/Autoupdate\n")
            append("\truns = 1\n")
            if (pid != null) {
                append("\tpid = $pid\n")
            }
            append("\tendpoints = {\n")
            append("\t\t\"example\" = {\n")
            append("\t\t\tstate = active\n")
            append("\t\t}\n")
            append("\t}\n")
            append("}\n")
        }
    }

    private companion object {
        const val LABEL: String = "app.posato.macos-sparkle-updater"
        const val NOT_FOUND_EXIT: Int = 113
    }
}
