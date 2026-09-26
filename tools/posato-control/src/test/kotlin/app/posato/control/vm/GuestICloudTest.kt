package app.posato.control.vm

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
}
