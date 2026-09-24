package app.posato.provisioning.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalDevicesTest {
    @Test
    fun `takes the Mac's provisioning identifier and not its platform identifier`() {
        // On Apple silicon these are different values: the platform UUID is a 36-character UUID, while the
        // Provisioning UDID Apple registers is 25 characters. Registering the wrong one produces an account record
        // that no profile will ever match, and nothing about the failure says so.
        val json =
            """
            {"SPHardwareDataType":[{"platform_UUID":"11111111-2222-3333-4444-555555555555",
            "provisioning_UDID":"00001111-000A22223333001E"}]}
            """.trimIndent()

        assertEquals("00001111-000A22223333001E", LocalDevices.parseMacUdid(json))
    }

    @Test
    fun `never falls back to the platform identifier`() {
        // Apple accepts the platform UUID, so a fallback would register successfully, report the Mac as registered,
        // and permanently consume one of the team's device slots for a record no profile can ever match. Reporting
        // nothing lets doctor say the identifier could not be read, which is recoverable.
        val json = """{"SPHardwareDataType":[{"platform_UUID":"11111111-2222-3333-4444-555555555555"}]}"""

        assertNull(LocalDevices.parseMacUdid(json))
    }

    @Test
    fun `reports nothing rather than guessing when the hardware output is unusable`() {
        assertNull(LocalDevices.parseMacUdid("not json"))
        assertNull(LocalDevices.parseMacUdid("""{"SPHardwareDataType":[]}"""))
        assertTrue(LocalDevices.parseConnectedIphones("not json").isEmpty())
    }

    // Every identifier below is a made-up sequence in the right shape. No real device identifier is committed.
    @Test
    fun `takes each wired iPhone's hardware identifier and not its connection identifier`() {
        // devicectl's `identifier` is a 36-character connection identifier, while the identifier Apple registers is
        // `hardwareProperties.udid`.
        //
        // Selection is on transportType, not tunnelState. A phone paired over the local network also reports a
        // tunnel, and that tunnel's state changes between readings, so selecting on it would register a different
        // set of devices depending on when the command ran, and would let a phone that merely shares the network
        // consume one of the team's limited device slots.
        val json =
            """
            {"result":{"devices":[
              {"identifier":"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"iOS","udid":"00008030-000102030405061E"},
               "connectionProperties":{"transportType":"wired","tunnelState":"connected"}},
              {"identifier":"FFFFFFFF-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"iOS","udid":"00008030-0A0B0C0D0E0F101E"},
               "connectionProperties":{"transportType":"localNetwork","tunnelState":"connected"}},
              {"identifier":"99999999-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"macOS","udid":"00008030-AAAAAAAAAAAAAA1E"},
               "connectionProperties":{"transportType":"wired","tunnelState":"connected"}}
            ]}}
            """.trimIndent()

        assertEquals(listOf("00008030-000102030405061E"), LocalDevices.parseConnectedIphones(json))
    }

    @Test
    fun `reads a Tart guest's provisioning identifier through tart exec and registers it as a secret`() {
        val runner = RecordingRunner.succeeding("""{"SPHardwareDataType":[{"provisioning_UDID":"0000FAKE0000VM000000"}]}""")
        val secrets = mutableListOf<String>()

        val device = LocalDeviceReader(runner) { secrets.add(it) }.tartVm("verification-golden")

        assertEquals("0000FAKE0000VM000000", device?.udid)
        assertEquals(
            listOf("tart", "exec", "verification-golden", "/usr/sbin/system_profiler", "SPHardwareDataType", "-json"),
            runner.commands.single(),
        )
        assertEquals(listOf("0000FAKE0000VM000000"), secrets)
    }

    @Test
    fun `a guest that cannot be read yields no device`() {
        assertNull(LocalDeviceReader(RecordingRunner.failing("VM is not running")) {}.tartVm("stopped-vm"))
    }
}
