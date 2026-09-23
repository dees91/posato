package app.posato.desktop.update

import java.io.File

internal object MacUpdater : UpdaterReplies {
    @Volatile private var controller: UpdaterController? = null

    fun start(updaterController: UpdaterController): Boolean {
        val resourcesDirectory = System.getProperty("compose.application.resources.dir") ?: return false
        val library = File(resourcesDirectory, "native/libPosatoUpdater.dylib")
        if (!library.isFile) {
            return false
        }
        return try {
            System.load(library.absolutePath)
            controller = updaterController
            nativeStart()
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    override fun completeAdmission(
        token: Long,
        granted: Boolean,
        refusal: AdmissionRefusal?,
    ) {
        nativeCompleteAdmission(token, granted, if (refusal == AdmissionRefusal.SESSION_ACTIVE) SESSION_ACTIVE_REFUSAL else OTHER_REFUSAL)
    }

    @JvmStatic
    fun onInstallRequested(
        token: Long,
        targetBuild: String,
        stage: Int,
    ) {
        val active = controller
        if (active == null) {
            nativeCompleteAdmission(token, false, OTHER_REFUSAL)
            return
        }
        val requestStage = if (stage == PENDING_STAGE) InstallRequestStage.PENDING_INSTALLATION else InstallRequestStage.NEW_INSTALLATION
        active.onInstallRequested(token, targetBuild, requestStage)
    }

    @JvmStatic
    fun onCycleFinished() {
        controller?.onCycleFinished()
    }

    private external fun nativeStart(): Boolean

    private external fun nativeCompleteAdmission(
        token: Long,
        granted: Boolean,
        refusal: Int,
    )
}

private const val PENDING_STAGE: Int = 1
private const val SESSION_ACTIVE_REFUSAL: Int = 1
private const val OTHER_REFUSAL: Int = 0
