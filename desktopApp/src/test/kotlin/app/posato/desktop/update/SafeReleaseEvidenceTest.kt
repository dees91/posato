package app.posato.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeReleaseEvidenceTest {
    @Test
    fun `given every domain absent and no autoupdate process when combined then the installer is terminated`() {
        val observation = combineInstallerObservations(listOf(LaunchctlJobState.ABSENT, LaunchctlJobState.NOT_RUNNING), autoupdateRunning = false)

        assertEquals(InstallerObservation.TERMINATED, observation)
    }

    @Test
    fun `given any running domain or autoupdate process when combined then the installer is running`() {
        assertEquals(
            InstallerObservation.RUNNING,
            combineInstallerObservations(listOf(LaunchctlJobState.ABSENT, LaunchctlJobState.RUNNING), autoupdateRunning = false),
        )
        assertEquals(
            InstallerObservation.RUNNING,
            combineInstallerObservations(listOf(LaunchctlJobState.ABSENT, LaunchctlJobState.ABSENT), autoupdateRunning = true),
        )
    }

    @Test
    fun `given an unknown domain or unreadable process list when combined then the installer state is unknown`() {
        assertEquals(
            InstallerObservation.UNKNOWN,
            combineInstallerObservations(listOf(LaunchctlJobState.ABSENT, LaunchctlJobState.UNKNOWN), autoupdateRunning = false),
        )
        assertEquals(
            InstallerObservation.UNKNOWN,
            combineInstallerObservations(listOf(LaunchctlJobState.ABSENT), autoupdateRunning = null),
        )
        assertEquals(InstallerObservation.UNKNOWN, combineInstallerObservations(emptyList(), autoupdateRunning = false))
    }

    @Test
    fun `given a terminated installer and a replaced signed bundle when evaluated then replacement is settled`() {
        assertTrue(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("9", "9", signedByTeam = true)))
    }

    @Test
    fun `given a terminated installer and the unchanged from build when evaluated then replacement is settled`() {
        assertTrue(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("8", "8", signedByTeam = true)))
    }

    @Test
    fun `given a manually installed newer signed build when evaluated then replacement is settled`() {
        assertTrue(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("12", "12", signedByTeam = true)))
    }

    @Test
    fun `given a running or unknown installer when evaluated then replacement is not settled for any bundle`() {
        listOf(InstallerObservation.RUNNING, InstallerObservation.UNKNOWN).forEach { installer ->
            assertFalse(replacementSettled(GATE, installer, BundleIdentity("9", "9", signedByTeam = true)), "$installer")
            assertFalse(replacementSettled(GATE, installer, BundleIdentity("8", "8", signedByTeam = true)), "$installer")
        }
    }

    @Test
    fun `given a bundle replaced on disk under the running process when evaluated then replacement is not settled`() {
        assertFalse(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("8", "9", signedByTeam = true)))
        assertFalse(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("8", null, signedByTeam = true)))
    }

    @Test
    fun `given an unsigned or foreign bundle when evaluated then replacement is not settled`() {
        assertFalse(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("9", "9", signedByTeam = false)))
    }

    @Test
    fun `given an older or non-numeric unrelated build when evaluated then replacement is not settled`() {
        assertFalse(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("7", "7", signedByTeam = true)))
        assertFalse(replacementSettled(GATE, InstallerObservation.TERMINATED, BundleIdentity("8a", "8a", signedByTeam = true)))
    }

    private companion object {
        val GATE: GateBuilds = GateBuilds(fromBuild = "8", targetBuild = "9")
    }
}
