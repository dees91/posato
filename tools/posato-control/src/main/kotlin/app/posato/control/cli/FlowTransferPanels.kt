package app.posato.control.cli

import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.desktop.AxBridge
import app.posato.control.linux.LinuxGuest
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.Step
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/**
 * A file panel with no tree of its own, driven by keys as a person who knows the path would: it opens with its
 * name field focused. Linux's GTK chooser is one.
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
        val AFTER_OPEN = setOf(TransferScreens.CODE_TITLE, TransferScreens.REVIEW_TITLE, TransferScreens.FAILURE_TITLE)
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
 * The Mac's save and open panels, in the guest where the whole command runs. The panels run in a separate service
 * that keys sent to Posato do not reach, but their tree is Posato's, so they are driven by accessibility: the Where
 * menu moves them to the home folder, which needs no privacy consent for the tool to read, a file row is clicked,
 * since it takes no accessibility selection, and Save or Open is pressed. The file to import is put in the guest with `vm push`.
 */
internal class MacTransferFiles(
    private val bridge: AxBridge,
) : TransferFiles {
    private val home: Path = Path.of(System.getProperty("user.home"))

    override fun clear() {
        Files.deleteIfExists(home.resolve(EXPORT_NAME))
    }

    override fun save(backend: Backend) {
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(id = SAVE_PANEL))))
        toHome(backend)
        FlowSteps.run(backend, listOf(FlowSteps.tap(Query(id = CONFIRM))))
    }

    override fun fetch(destination: Path) {
        val saved = home.resolve(EXPORT_NAME)
        if (!Files.isRegularFile(saved)) throw ControlException(ErrorCode.COMMAND_FAILED, "No export was saved in $home.")
        Files.copy(saved, destination)
    }

    override fun place(source: Path): String {
        val placed = home.resolve(source.fileName.toString())
        if (source.toAbsolutePath() != placed) Files.copy(source, placed, StandardCopyOption.REPLACE_EXISTING)
        return placed.toString()
    }

    override fun choose(
        backend: Backend,
        placed: String,
    ) {
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(id = OPEN_PANEL))))
        toHome(backend)
        val name = Path.of(placed).fileName.toString()
        val row = FlowSteps.nodes(backend).firstOrNull { node -> node.value == name }?.path
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The open panel lists no $name in $home.")
        val pid = backend.status().pid ?: throw ControlException(ErrorCode.APP_NOT_RUNNING, "Posato is not running.")
        bridge.windowActions.click(pid, row)
        FlowSteps.run(backend, listOf(FlowSteps.sleep(SETTLE_SECONDS), FlowSteps.tap(Query(id = CONFIRM))))
    }

    override fun cleanUp(placed: String) = Unit

    /** Picking the folder a panel already shows leaves the Where menu open over the rows, so it is picked only to move. */
    private fun toHome(backend: Backend) {
        val folder = home.fileName.toString()
        if (FlowSteps.nodes(backend).any { node -> node.id == WHERE && node.value == folder }) return
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.tap(Query(id = WHERE)),
                FlowSteps.sleep(SETTLE_SECONDS),
                FlowSteps.tap(Query(text = folder, within = Query(id = WHERE), index = 0)),
                FlowSteps.sleep(SETTLE_SECONDS),
            ),
        )
    }

    private companion object {
        const val SAVE_PANEL = "save-panel"
        const val OPEN_PANEL = "open-panel"
        const val WHERE = "where popup"
        const val CONFIRM = "OKButton"
        const val SETTLE_SECONDS = 1.0
    }
}

/** A key with modifiers, sent to whatever has focus, such as a file panel with no tree of its own. */
private fun press(
    key: String,
    vararg modifiers: String,
): Step = Step(action = Actions.PRESS, key = key, modifiers = modifiers.toList())
