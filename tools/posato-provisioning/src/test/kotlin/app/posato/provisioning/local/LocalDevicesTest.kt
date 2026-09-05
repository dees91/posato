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
    fun `falls back to the platform identifier only where no provisioning identifier is reported`() {
        val json = """{"SPHardwareDataType":[{"platform_UUID":"11111111-2222-3333-4444-555555555555"}]}"""

        assertEquals("11111111-2222-3333-4444-555555555555", LocalDevices.parseMacUdid(json))
    }

    @Test
    fun `reports nothing rather than guessing when the hardware output is unusable`() {
        assertNull(LocalDevices.parseMacUdid("not json"))
        assertNull(LocalDevices.parseMacUdid("""{"SPHardwareDataType":[]}"""))
        assertTrue(LocalDevices.parseConnectedIphones("not json").isEmpty())
    }

    // Every identifier below is a made-up sequence in the right shape. No real device identifier is committed.
    @Test
    fun `takes each connected iPhone's hardware identifier and not its connection identifier`() {
        // devicectl's `identifier` is a 36-character connection identifier, while the identifier Apple registers is
        // `hardwareProperties.udid`. Only a connected iPhone is registered, so a paired phone that is not in use
        // does not silently consume one of the team's limited device slots.
        val json =
            """
            {"result":{"devices":[
              {"identifier":"AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"iOS","udid":"00008030-000102030405061E"},
               "connectionProperties":{"tunnelState":"connected"}},
              {"identifier":"FFFFFFFF-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"iOS","udid":"00008030-0A0B0C0D0E0F101E"},
               "connectionProperties":{"tunnelState":"disconnected"}},
              {"identifier":"99999999-BBBB-CCCC-DDDD-EEEEEEEEEEEE",
               "hardwareProperties":{"platform":"macOS","udid":"00008030-AAAAAAAAAAAAAA1E"},
               "connectionProperties":{"tunnelState":"connected"}}
            ]}}
            """.trimIndent()

        assertEquals(listOf("00008030-000102030405061E"), LocalDevices.parseConnectedIphones(json))
    }
}
