package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.model.Query
import app.posato.control.model.States

/**
 * Android's system document picker (DocumentsUI), which export, import, and the folder workspace open. It opens where
 * it was used last, so each use starts from this phone's storage, reached through its breadcrumb or Show roots.
 */
internal class AndroidDocumentsUi(
    private val device: AndroidDevice,
) {
    /** Brings the open picker to the top of this phone's shared storage, where adb reads and writes too. */
    fun openPhoneStorage(backend: Backend) {
        val phone = device.shell("settings get global device_name").stdout.trim()
        val crumb = Query(id = BREADCRUMB, text = phone)
        val steps = if (FlowSteps.nodes(backend).any { node -> node.id == BREADCRUMB && node.label == phone }) {
            listOf(FlowSteps.tap(crumb))
        } else {
            listOf(FlowSteps.tap(Query(text = "Show roots")), FlowSteps.tap(Query(text = phone)))
        }
        FlowSteps.run(backend, steps + FlowSteps.waitFor(Query(id = HEADER, text = "Files on $phone")))
    }

    /** Opens [name], a folder in the folder the picker shows. */
    fun openFolder(
        backend: Backend,
        name: String,
    ) {
        FlowSteps.run(backend, listOf(FlowSteps.tap(Query(id = ITEM, text = name)), FlowSteps.waitFor(Query(id = HEADER, text = "Files in $name"))))
    }

    /**
     * Picks [path] on shared storage (`/sdcard/...` or `/storage/emulated/0/...`) in the folder picker: from the top
     * of the phone's storage through each folder, then the picker's select button and Allow in its confirmation.
     */
    fun pickFolder(
        backend: Backend,
        path: String,
    ) {
        val relative = SHARED_STORAGE.firstNotNullOfOrNull { root -> path.removePrefix(root).takeIf { it != path } }
            ?.split('/')?.filter(String::isNotEmpty)
        if (relative.isNullOrEmpty()) {
            throw ControlException(ErrorCode.USAGE, "On Android --path names a folder on shared storage, such as /sdcard/PosatoSync.")
        }
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(id = HEADER))))
        openPhoneStorage(backend)
        relative.forEach { name -> openFolder(backend, name) }
        // The select button and the confirmation's Allow are both the window's first button; only the confirmation has Cancel.
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.tap(Query(id = FIRST_BUTTON)),
                FlowSteps.waitFor(Query(id = SECOND_BUTTON)),
                FlowSteps.tap(Query(id = FIRST_BUTTON)),
                FlowSteps.waitFor(Query(id = HEADER), state = States.ABSENT),
            ),
        )
    }

    companion object {
        const val BREADCRUMB = "com.google.android.documentsui:id/breadcrumb_text"
        const val HEADER = "com.google.android.documentsui:id/header_title"
        const val ITEM = "com.google.android.documentsui:id/item_root"
        const val FIRST_BUTTON = "android:id/button1"
        const val SECOND_BUTTON = "android:id/button2"
        private val SHARED_STORAGE = listOf("/sdcard/", "/storage/emulated/0/", "/storage/self/primary/")
    }
}
