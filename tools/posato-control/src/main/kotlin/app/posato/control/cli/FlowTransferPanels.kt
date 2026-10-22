package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.linux.LinuxGuest
import app.posato.control.model.Actions
import app.posato.control.model.Step
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * The file panels of the Mac and Linux, AWT's file dialog over Posato's window. Neither shows in Posato's tree, so
 * they are driven by keys, as a person who knows the path would: the panel opens with its name field focused.
 */
internal abstract class PanelTransferFiles : TransferFiles {
    /** Where exports land and files to import are placed, on the device. */
    protected abstract val folder: String

    /** Types [path] into the panel and confirms; the panel offers no tree to wait on, so it is given time. */
    protected fun enter(
        backend: Backend,
        path: String,
        open: List<String>,
    ) {
        FlowSteps.run(
            backend,
            listOf(FlowSteps.sleep(PANEL_SECONDS)) + open.map { key -> press(key.substringAfterLast('+'), *modifiers(key)) },
        )
        Thread.sleep(SETTLE_MILLIS)
        backend.typeFocused(path, clear = true, submit = false)
        // A completion list can take the first Return, so a second follows while Posato's own dialog is not up.
        FlowSteps.run(backend, listOf(FlowSteps.sleep(PANEL_SECONDS), press("return")))
    }

    /** Presses Return again when the open panel kept the first one, until Posato asks for a code or a mode. */
    protected fun confirmOpened(backend: Backend) {
        repeat(OPEN_ATTEMPTS) {
            Thread.sleep(SETTLE_MILLIS)
            if (FlowSteps.labels(backend).any { label -> label in AFTER_OPEN }) return
            FlowSteps.run(backend, listOf(press("return")))
        }
    }

    private fun modifiers(chord: String): Array<String> = chord.split('+').dropLast(1).toTypedArray()

    private companion object {
        const val PANEL_SECONDS = 2.0
        const val SETTLE_MILLIS = 1_500L
        const val OPEN_ATTEMPTS = 3
        val AFTER_OPEN = setOf("Enter the code", "Choose how to import", "This file can't be read")
    }
}

/**
 * The GTK chooser in the Linux clone. Its name field takes a whole path when saving; when opening, Ctrl+L brings
 * up the location field. The host passes files through `linux exec`, so the clone's folder is in `/tmp`.
 */
internal class LinuxTransferFiles(
    private val guest: LinuxGuest,
) : PanelTransferFiles() {
    override val folder = "/tmp/posato-transfer"

    override fun clear() {
        guest.exec("mkdir -p $folder && rm -f '$folder/$EXPORT_NAME'").requireSuccess(ErrorCode.COMMAND_FAILED, "Clearing $folder")
    }

    override fun save(backend: Backend) = enter(backend, "$folder/$EXPORT_NAME", listOf("ctrl+a"))

    override fun fetch(destination: Path) {
        val encoded = guest.exec("base64 -w0 '$folder/$EXPORT_NAME'").requireSuccess(ErrorCode.COMMAND_FAILED, "Reading the export").stdout.trim()
        Files.write(destination, Base64.getDecoder().decode(encoded))
    }

    override fun place(source: Path): String {
        val placed = "$folder/${source.fileName}"
        guest.exec("mkdir -p $folder && base64 -d > '$placed'", Base64.getEncoder().encodeToString(Files.readAllBytes(source)))
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Placing the file in $folder")
        return placed
    }

    override fun choose(
        backend: Backend,
        placed: String,
    ) {
        enter(backend, placed, listOf("ctrl+l"))
        confirmOpened(backend)
    }

    override fun cleanUp(placed: String) {
        guest.exec("rm -f '$placed'")
    }
}

/**
 * The Mac's save and open panels, in the guest where the whole command runs: Go to Folder (Command-Shift-G) takes
 * a folder when saving and a file when opening. The file to import is already in the guest, put there with
 * `vm push`, and the export is read where it was saved.
 */
internal class MacTransferFiles : PanelTransferFiles() {
    override val folder: String = Path.of(System.getProperty("user.home"), "PosatoTransfer").toString()

    override fun clear() {
        Files.createDirectories(Path.of(folder))
        Files.deleteIfExists(Path.of(folder, EXPORT_NAME))
    }

    override fun save(backend: Backend) {
        enter(backend, folder, listOf("cmd+shift+g"))
        // Go to Folder only moves the panel; Return then saves under the name it proposes.
        FlowSteps.run(backend, listOf(FlowSteps.sleep(1.0), press("return")))
    }

    override fun fetch(destination: Path) {
        val saved = Path.of(folder, EXPORT_NAME)
        if (!Files.isRegularFile(saved)) throw ControlException(ErrorCode.COMMAND_FAILED, "No export was saved in $folder.")
        Files.copy(saved, destination)
    }

    override fun place(source: Path): String = source.toString()

    override fun choose(
        backend: Backend,
        placed: String,
    ) {
        enter(backend, placed, listOf("cmd+shift+g"))
        confirmOpened(backend)
    }

    override fun cleanUp(placed: String) = Unit
}

/** A key with modifiers, sent to whatever has focus, such as a file panel with no tree of its own. */
private fun press(
    key: String,
    vararg modifiers: String,
): Step = Step(action = Actions.PRESS, key = key, modifiers = modifiers.toList())
