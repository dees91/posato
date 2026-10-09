package app.posato.control.vm

import app.posato.control.core.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuestICloudTest {
    private fun line(text: String) = RecognizedLine(text, 1.0, 0, 0, 10, 10)

    @Test
    fun `given the sidebar notice when reading the state then iCloud Keychain is paused`() {
        val lines = listOf(line("Piotr Example"), line("Apple Account"), line("Some iCloud Data"), line("Isn’t Syncing"))

        assertEquals(ICloudKeychainState.PAUSED, iCloudKeychainState(lines))
    }

    @Test
    fun `given only the account row when reading the state then iCloud Keychain syncs`() {
        assertEquals(ICloudKeychainState.SYNCING, iCloudKeychainState(listOf(line("Apple Account"), line("Wi-Fi"))))
    }

    @Test
    fun `given the sign-in row when reading the state then the guest is signed out, not syncing`() {
        assertEquals(ICloudKeychainState.SIGNED_OUT, iCloudKeychainState(listOf(line("Sign in"), line("with your Apple Account"))))
        assertEquals(ICloudKeychainState.SIGNED_OUT, iCloudKeychainState(listOf(line("Sign in with your Apple Account"))))
    }

    @Test
    fun `given the connect alert over the sidebar when reading the state then the account needs attention and iCloud is refused`() {
        // The alert's own text names "Apple Account", which once read as a syncing keychain (`observed` 2026-10-08).
        val lines = listOf(
            line("This Mac can’t connect to iCloud"),
            line("because of a problem with \"qa@example.com\"."),
            line("Open Apple Account settings to fix this problem."),
            line("Apple Account Settings..."),
            line("Later"),
        )

        assertEquals(ICloudKeychainState.NEEDS_ATTENTION, iCloudKeychainState(lines))
        assertEquals(ErrorCode.ICLOUD_KEYCHAIN_PAUSED, keychainRefusal(VmLine.PRIMARY, ICloudKeychainState.NEEDS_ATTENTION)?.code)
    }

    @Test
    fun `given the connect alert over a paused keychain when reading the state then the keychain is paused and can be resumed`() {
        // Resume Data Sync answers the alert with Later and may need only the account password, so it keeps running.
        val lines = listOf(line("This Mac can’t connect to iCloud"), line("Later"), line("Some iCloud Data"), line("Isn’t Syncing"))

        assertEquals(ICloudKeychainState.PAUSED, iCloudKeychainState(lines))
    }

    @Test
    fun `given neither row when reading the state then the state is unknown`() {
        assertEquals(ICloudKeychainState.UNKNOWN, iCloudKeychainState(listOf(line("General"))))
    }

    @Test
    fun `given each resume dialog when choosing the answer then the matching prompt is used`() {
        assertEquals(GuestPrompt.ACCOUNT_PASSWORD, pendingICloudPrompt(listOf(line("Enter the Apple Account password for \"qa@example.com\"."))))
        assertEquals(GuestPrompt.MAC_PASSWORD, pendingICloudPrompt(listOf(line("Enter Mac Password"))))
        assertEquals(GuestPrompt.DEVICE_PASSCODE, pendingICloudPrompt(listOf(line("Enter the passcode you use to unlock"))))
        assertNull(pendingICloudPrompt(listOf(line("Updating your account..."))))
    }

    @Test
    fun `given the picker-bypass request over a resume dialog when choosing the answer then the request is answered first`() {
        val lines = listOf(line("\"tart-guest-agent\" is requesting to bypass the system private window picker"), line("Enter Mac Password"))

        assertEquals(GuestPrompt.PICKER_BYPASS, pendingICloudPrompt(lines))
    }

    @Test
    fun `given the connect alert over the password sheet when choosing the answer then the alert is dismissed first`() {
        val lines = listOf(line("This Mac can't connect to iCloud"), line("Enter the Apple Account password for"), line("Later"))

        assertEquals(GuestPrompt.ICLOUD_LATER, pendingICloudPrompt(lines))
    }
}
