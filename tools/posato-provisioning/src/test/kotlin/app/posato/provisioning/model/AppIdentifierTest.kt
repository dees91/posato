package app.posato.provisioning.model

import app.posato.provisioning.core.ProvisioningException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppIdentifierTest {
    @Test
    fun `pins the file the macOS sync profile must keep so the configured property still resolves`() {
        // SYNC-006 created this file by hand and posato.macos.syncProvisioningProfile points at it. Renaming it here
        // would leave the desktop packaging task looking at a stale profile without any error saying so.
        assertEquals("Posato_macOS_Sync_Development.provisionprofile", AppIdentifier.MACOS_SYNC.fileName)
    }

    @Test
    fun `gives every App ID the platform and profile type its target needs`() {
        AppIdentifier.entries.forEach { identifier ->
            val iosNamed = identifier.bundleId.startsWith("app.posato.ios")
            assertEquals(if (iosNamed) ApplePlatform.IOS else ApplePlatform.MACOS, identifier.platform, identifier.bundleId)
            assertEquals(
                if (iosNamed) "IOS_APP_DEVELOPMENT" else "MAC_APP_DEVELOPMENT",
                identifier.profileType,
                identifier.bundleId,
            )
            assertEquals(if (iosNamed) "mobileprovision" else "provisionprofile", identifier.fileExtension, identifier.bundleId)
        }
    }

    @Test
    fun `covers the five registered App IDs and nothing else`() {
        assertEquals(
            listOf(
                "app.posato.ios",
                "app.posato.ios.activitymonitor",
                "app.posato.macos",
                "app.posato.macos.helper",
                "app.posato.macos.sync",
            ),
            AppIdentifier.entries.map { it.bundleId }.sorted(),
        )
    }

    @Test
    fun `names the accepted App IDs when given one it does not know`() {
        val failure = assertFailsWith<ProvisioningException> { AppIdentifier.of("app.posato.android") }

        assertTrue(failure.hint.orEmpty().contains("app.posato.macos.sync"))
    }
}
