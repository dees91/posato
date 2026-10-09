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
import app.posato.desktop.mappings.ApplicationRequirements
import app.posato.desktop.mappings.DesktopLocalApplicationMappings
import app.posato.desktop.mappings.keptApplications
import app.posato.desktop.session.MacOsApplicationEnforcementLink
import app.posato.desktop.session.MacOsBrowserEnforcementLink
import app.posato.desktop.update.FileInstanceLock
import app.posato.desktop.update.createUpdaterController
import app.posato.desktop.update.openInstanceLock
import app.posato.di.DesktopApplicationComponents
import app.posato.di.createDesktopApplicationGraph
import app.posato.feature.enforcement.JvmSessionEnforcement
import app.posato.feature.onboarding.MacConsole
import app.posato.feature.onboarding.MacHelperOperations
import app.posato.feature.presence.loadPresenceCopy
import app.posato.feature.session.data.KeptApplicationRequirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (!TranslationGuard.permitsLaunch()) {
        exitProcess(0)
    }
    MacPresenceNative.installLaunchProbe()
    val instanceLock = openInstanceLock()
    if (!instanceLock.acquireShared()) {
        exitProcess(0)
    }
    MacOsHelperClient().use { enforcementClient ->
        DesktopLocalApplicationMappings().use { applicationMappings ->
            Runtime.getRuntime().addShutdownHook(Thread(applicationMappings::close, "application-mappings-shutdown"))
            // The graph that reads what running parts kept is built after enforcement, which it depends on.
            var keptRequirements: KeptApplicationRequirements? = null
            val requirements = ApplicationRequirements(applicationMappings) { ids -> keptRequirements?.of(ids).orEmpty() }
            val enforcement = JvmSessionEnforcement(
                MacOsBrowserEnforcementLink(MacOsBrowserDomainEnforcer(enforcementClient), enforcementClient),
                MacOsApplicationEnforcementLink(
                    MacOsApplicationEnforcer(enforcementClient, requirements::resolve),
                    applicationMappings,
                    enforcementClient,
                ),
            )
            val applicationGraph = createDesktopApplicationGraph(
                applicationMappings,
                enforcement,
                createHelperState(enforcementClient),
                notifications = MacSessionNotifications,
            )
            keptRequirements = applicationGraph.keptApplicationRequirements
            if (args.firstOrNull() == VERIFICATION_ARGUMENT) {
                runVerificationSeam(applicationGraph, instanceLock, args.getOrNull(1).orEmpty())
            }
            preparePauseSets(applicationGraph, applicationMappings)
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

/** Whether this account has the console, so a scheduled start waits while another account uses the Mac. */
private val consoleOfThisAccount = MacConsole {
    when (MacPresenceNative.consoleIsOurs()) {
        1 -> true
        0 -> false
        else -> null
    }
}

private fun createHelperState(enforcementClient: MacOsHelperClient): DesktopMacHelperState {
    val helperOperations = MacHelperOperations()
    return DesktopMacHelperState(
        commands = enforcementClient,
        verifyHelper = {
            MacOsHelperSigningVerifier.verify(MacOsHelperSigningVerifier.installedHelperPath())
        },
        ioDispatcher = Dispatchers.IO,
        openSettings = MacOsSystemSettings::open,
        loginItem = MacLoginItemState,
        standingGrant = DesktopStandingGrant(enforcementClient, Dispatchers.IO, helperOperations),
        offerFlag = MacSetupOfferFlag(
            read = { MacNotificationsNative.readFlag(SETUP_OFFER_DISMISSED_KEY) == 1 },
            write = { MacNotificationsNative.writeFlag(SETUP_OFFER_DISMISSED_KEY, true) },
        ),
        operations = helperOperations,
        console = consoleOfThisAccount,
        automaticStartConsent = MacAutomaticStartConsentFlag(
            read = { MacNotificationsNative.readFlag(AUTOMATIC_START_CONSENT_KEY) == 1 },
            write = { given -> MacNotificationsNative.writeFlag(AUTOMATIC_START_CONSENT_KEY, given) },
        ),
    )
}

/** Before any host or window starts: the one-time pause set upgrade, then removal of choices for sets that are gone. */
private fun preparePauseSets(
    graph: DesktopApplicationComponents,
    mappings: DesktopLocalApplicationMappings,
) {
    try {
        runBlocking { graph.pauseSetPreparation.prepare { mappings.keptApplications() } }
    } catch (_: Exception) {
        // A failed step stays pending and runs again at the next launch.
    }
}

private const val VERIFICATION_ARGUMENT = "--posato-verification"

/**
 * Runs one verification seam (ADR 0007 amendment of 2026-10-09) instead of the application and exits. It needs
 * the only running instance; the seams themselves stay inert unless this package carries their key.
 */
private fun runVerificationSeam(
    applicationGraph: DesktopApplicationComponents,
    instanceLock: FileInstanceLock,
    command: String,
): Nothing {
    if (!instanceLock.tryUpgradeForAdmission()) {
        println("{\"outcome\":\"another-instance\"}")
        exitProcess(VERIFICATION_REFUSED)
    }
    println(runBlocking { applicationGraph.verificationSeams.run(command) })
    exitProcess(0)
}

private const val VERIFICATION_REFUSED = 3
