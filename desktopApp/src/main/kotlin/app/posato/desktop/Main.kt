package app.posato.desktop

import androidx.compose.ui.window.application
import app.posato.desktop.macos.AUTOMATIC_START_CONSENT_KEY
import app.posato.desktop.macos.DesktopMacHelperState
import app.posato.desktop.macos.DesktopStandingGrant
import app.posato.desktop.macos.MacAutomaticStartConsentFlag
import app.posato.desktop.macos.MacOsApplicationEnforcer
import app.posato.desktop.macos.MacOsBrowserDomainEnforcer
import app.posato.desktop.macos.MacOsHelperClient
import app.posato.desktop.macos.MacOsHelperSigningVerifier
import app.posato.desktop.macos.MacOsSystemSettings
import app.posato.desktop.macos.MacSetupOfferFlag
import app.posato.desktop.macos.SETUP_OFFER_DISMISSED_KEY
import app.posato.desktop.mappings.DesktopLocalApplicationMappings
import app.posato.desktop.session.MacOsApplicationEnforcementLink
import app.posato.desktop.session.MacOsBrowserEnforcementLink
import app.posato.desktop.update.createUpdaterController
import app.posato.desktop.update.openInstanceLock
import app.posato.di.createDesktopApplicationGraph
import app.posato.feature.enforcement.JvmSessionEnforcement
import app.posato.feature.presence.loadPresenceCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main() {
    MacPresenceNative.installLaunchProbe()
    val instanceLock = openInstanceLock()
    if (!instanceLock.acquireShared()) {
        exitProcess(0)
    }
    MacOsHelperClient().use { enforcementClient ->
        DesktopLocalApplicationMappings().use { applicationMappings ->
            Runtime.getRuntime().addShutdownHook(Thread(applicationMappings::close, "application-mappings-shutdown"))
            val enforcement = JvmSessionEnforcement(
                MacOsBrowserEnforcementLink(MacOsBrowserDomainEnforcer(enforcementClient), enforcementClient),
                MacOsApplicationEnforcementLink(
                    MacOsApplicationEnforcer(enforcementClient, applicationMappings::designatedRequirements),
                    applicationMappings,
                    enforcementClient,
                ),
            )
            val helperState = DesktopMacHelperState(
                commands = enforcementClient,
                verifyHelper = {
                    MacOsHelperSigningVerifier.verify(MacOsHelperSigningVerifier.installedHelperPath())
                },
                ioDispatcher = Dispatchers.IO,
                openSettings = MacOsSystemSettings::open,
                loginItem = MacLoginItemState,
                standingGrant = DesktopStandingGrant(enforcementClient, Dispatchers.IO),
                offerFlag = MacSetupOfferFlag(
                    read = { MacNotificationsNative.readFlag(SETUP_OFFER_DISMISSED_KEY) == 1 },
                    write = { MacNotificationsNative.writeFlag(SETUP_OFFER_DISMISSED_KEY, true) },
                ),
                automaticStartConsent = MacAutomaticStartConsentFlag(
                    read = { MacNotificationsNative.readFlag(AUTOMATIC_START_CONSENT_KEY) == 1 },
                    write = { given -> MacNotificationsNative.writeFlag(AUTOMATIC_START_CONSENT_KEY, given) },
                ),
            )
            val applicationGraph = createDesktopApplicationGraph(
                applicationMappings,
                enforcement,
                helperState,
                notifications = MacSessionNotifications,
            )
            val updaterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val updater = createUpdaterController(enforcementClient, applicationGraph.updateMaintenance, instanceLock, updaterScope)
            runBlocking { updater.start() }
            val presenceCopy = runBlocking { loadPresenceCopy() }
            val launchedAtLogin = MacPresenceNative.launchedAtLogin()

            application {
                ResidentPosato(applicationGraph, updater, presenceCopy, launchedAtLogin)
            }
        }
    }
}
