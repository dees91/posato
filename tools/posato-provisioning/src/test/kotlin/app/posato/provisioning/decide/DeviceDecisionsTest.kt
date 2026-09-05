package app.posato.provisioning.decide

import app.posato.provisioning.local.LocalDevice
import app.posato.provisioning.model.ApplePlatform
import app.posato.provisioning.model.DeviceAttributes
import app.posato.provisioning.model.DeviceResource
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDecisionsTest {
    @Test
    fun `registers only what the account does not already hold`() {
        val mac = LocalDevice("MAC-UDID", ApplePlatform.MACOS)
        val phone = LocalDevice("PHONE-UDID", ApplePlatform.IOS)

        val decisions = DeviceDecisions.decide(listOf(mac, phone), listOf(enabled("MAC-UDID")))

        assertEquals(DeviceOutcome.ALREADY_REGISTERED, decisions[0].outcome)
        assertEquals(DeviceOutcome.REGISTERED, decisions[1].outcome)
        assertEquals(listOf(phone), decisions.filter { it.requiresCreate }.map { it.device })
    }

    @Test
    fun `reports a disabled device instead of creating it again`() {
        val decisions = DeviceDecisions.decide(
            listOf(LocalDevice("MAC-UDID", ApplePlatform.MACOS)),
            listOf(DeviceResource("ID", DeviceAttributes(udid = "MAC-UDID", status = "DISABLED"))),
        )

        assertEquals(DeviceOutcome.DISABLED, decisions.single().outcome)
        assertEquals(false, decisions.single().requiresCreate)
    }

    @Test
    fun `matches an identifier Apple stored with different letter case`() {
        val decisions = DeviceDecisions.decide(
            listOf(LocalDevice("abcd-1234", ApplePlatform.IOS)),
            listOf(enabled("ABCD-1234")),
        )

        assertEquals(DeviceOutcome.ALREADY_REGISTERED, decisions.single().outcome)
    }

    private fun enabled(udid: String): DeviceResource = DeviceResource("ID-$udid", DeviceAttributes(udid = udid, status = "ENABLED"))
}
