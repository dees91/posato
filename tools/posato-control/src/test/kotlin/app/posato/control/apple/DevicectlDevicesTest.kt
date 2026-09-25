package app.posato.control.apple

import app.posato.control.core.ControlJson
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevicectlDevicesTest {
    private fun result(vararg devices: String) = ControlJson.lenient.parseToJsonElement("""{"devices":[${devices.joinToString(",")}]}""").jsonObject

    private fun device(
        identifier: String,
        pairing: String,
        transport: String?,
        tunnel: String
    ): String {
        val transportField = transport?.let { "\"transportType\":\"$it\"," } ?: ""
        return """
            {"identifier":"$identifier",
             "deviceProperties":{"name":"Test iPhone $identifier","osVersionNumber":"26.0","developerModeStatus":"enabled"},
             "hardwareProperties":{"marketingName":"iPhone"},
             "connectionProperties":{"pairingState":"$pairing",$transportField"tunnelState":"$tunnel"}}
            """.trimIndent()
    }

    @Test
    fun `a wired paired device with an idle tunnel is listed as not connected and is the one to wake`() {
        val devices = parseDevices(result(device("A", "paired", "wired", "disconnected")))

        assertEquals(1, devices.size)
        assertFalse(devices.single().connected)
        assertTrue(devices.single().paired)
        assertEquals(listOf("A"), idleWiredDevices(devices).map { it.udid })
    }

    @Test
    fun `a connected device needs no wake`() {
        val devices = parseDevices(result(device("A", "paired", "wired", "connected")))

        assertTrue(devices.single().connected)
        assertTrue(idleWiredDevices(devices).isEmpty())
    }

    @Test
    fun `only paired devices on a cable are woken`() {
        val devices = parseDevices(
            result(
                device("wireless", "paired", "localNetwork", "disconnected"),
                device("unpaired", "unpaired", "wired", "disconnected"),
                device("unknown-transport", "paired", null, "disconnected"),
                device("cable", "paired", "wired", "disconnected"),
            ),
        )

        assertEquals(listOf("cable"), idleWiredDevices(devices).map { it.udid })
    }
}
