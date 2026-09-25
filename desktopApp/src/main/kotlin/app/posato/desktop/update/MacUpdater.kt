package app.posato.desktop.update

import app.posato.feature.about.ApplicationUpdates
import app.posato.feature.about.ApplicationUpdatesState
import app.posato.feature.update.UpdaterCopy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

internal object MacUpdater : UpdaterReplies {
    @Volatile private var controller: UpdaterController? = null

    fun start(
        updaterController: UpdaterController,
        copy: UpdaterCopy,
    ): Boolean {
        val resourcesDirectory = System.getProperty("compose.application.resources.dir") ?: return false
        val library = File(resourcesDirectory, "native/libPosatoUpdater.dylib")
        if (!library.isFile) {
            return false
        }
        return try {
            System.load(library.absolutePath)
            controller = updaterController
            nativeStart(copy.nativeOrder())
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    override fun completeAdmission(
        token: Long,
        granted: Boolean,
        refusal: AdmissionRefusal?,
    ) {
        nativeCompleteAdmission(token, granted, refusalCode(refusal))
    }

    @JvmStatic
    fun onInstallRequested(
        token: Long,
        targetBuild: String,
        stage: Int,
    ) {
        val active = controller
        if (active == null) {
            nativeCompleteAdmission(token, false, refusalCode(null))
            return
        }
        val requestStage = if (stage == PENDING_STAGE) InstallRequestStage.PENDING_INSTALLATION else InstallRequestStage.NEW_INSTALLATION
        active.onInstallRequested(token, targetBuild, requestStage)
    }

    @JvmStatic
    fun onCycleFinished() {
        controller?.onCycleFinished()
    }

    @JvmStatic
    fun onStateChanged(
        automaticChecks: Boolean,
        canCheckNow: Boolean,
    ) {
        MacUpdateSettings.publish(ApplicationUpdatesState(available = true, automaticChecks = automaticChecks, canCheckNow = canCheckNow))
    }

    private external fun nativeStart(copy: Array<String>): Boolean

    private external fun nativeCompleteAdmission(
        token: Long,
        granted: Boolean,
        refusal: Int,
    )
}

internal object MacUpdateSettings : ApplicationUpdates {
    private val updatesState = MutableStateFlow(ApplicationUpdatesState())

    override val state: StateFlow<ApplicationUpdatesState> = updatesState.asStateFlow()

    override fun setAutomaticChecks(enabled: Boolean) {
        nativeSetAutomaticChecks(enabled)
    }

    override fun checkNow() {
        nativeCheckForUpdates()
    }

    override fun askForAutomaticChecksOnce() {
        nativeAskForAutomaticChecksOnce()
    }

    fun publish(state: ApplicationUpdatesState) {
        updatesState.value = state
    }

    private external fun nativeSetAutomaticChecks(enabled: Boolean)

    private external fun nativeCheckForUpdates()

    private external fun nativeAskForAutomaticChecksOnce()
}

internal fun refusalCode(refusal: AdmissionRefusal?): Int {
    return when (refusal) {
        AdmissionRefusal.SESSION_ACTIVE -> SESSION_ACTIVE_REFUSAL
        AdmissionRefusal.OTHER_INSTANCE -> OTHER_INSTANCE_REFUSAL
        else -> OTHER_REFUSAL
    }
}

internal fun UpdaterCopy.nativeOrder(): Array<String> {
    return arrayOf(
        checkForUpdates,
        preparing,
        refusedTitle,
        refusedSession,
        refusedOtherInstance,
        refusedOther,
        consentTitle,
        consentMessage,
        consentAllow,
        consentDeny,
    )
}

private const val PENDING_STAGE: Int = 1
private const val OTHER_REFUSAL: Int = 0
private const val SESSION_ACTIVE_REFUSAL: Int = 1
private const val OTHER_INSTANCE_REFUSAL: Int = 2
