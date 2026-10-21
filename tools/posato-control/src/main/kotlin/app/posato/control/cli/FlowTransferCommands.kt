package app.posato.control.cli

import app.posato.control.android.AndroidDevice
import app.posato.control.apple.DeviceSession
import app.posato.control.apple.Devicectl
import app.posato.control.apple.XcodeBuild
import app.posato.control.backend.Backend
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import app.posato.control.model.Query
import app.posato.control.model.States
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports the pause sets through Pause sets' menu and the system's own save path, then copies the file to the
 * host: Android's document picker into Documents, and on the iPhone the share sheet's Save to Files into the
 * driver host's folder under On My iPhone. With --code the file is protected and the code is read from the dialog.
 */
class FlowExportCommand : ControlCommand("export", "Export the pause sets to a file and copy it to the host.") {
    private val out by option("--out", help = "Where to put the exported file on the host.").required()
    private val code by option("--code", help = "Protect the file with a code and print the code.").flag()

    override fun execute(session: Session): JsonElement {
        val backend = session.backend()
        val files = transferFiles(session)
        files.clear()
        TransferScreens.openMenu(backend, session.target(), "Export pause sets…")
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(text = "Export file…", role = FlowSteps.ROLE_BUTTON))))
        val shown = if (code) TransferScreens.protect(backend) else null
        FlowSteps.run(backend, listOf(FlowSteps.button("Export file…")))
        files.save(backend)
        // The app confirms a saved coded file with its code and closes the dialog after a saved plain one; a
        // cancelled or failed save leaves the dialog open, so an older file is never fetched as this run's.
        val saved = if (shown != null) {
            listOf(FlowSteps.waitFor(Query(text = "Exported")), FlowSteps.button("OK"))
        } else {
            listOf(FlowSteps.waitFor(Query(text = "Export file…", role = FlowSteps.ROLE_BUTTON), States.ABSENT))
        }
        FlowSteps.run(backend, saved)
        val target = Path.of(out).toAbsolutePath()
        files.fetch(target)
        return buildJsonObject {
            put("out", target.toString())
            put("bytes", Files.size(target))
            shown?.let { put("code", it) }
        }
    }
}

/**
 * Imports a file from the host through Pause sets' menu and the system's open path: the file is placed where the
 * picker can reach it, chosen there, opened with --code when it is protected, and imported in --mode.
 */
class FlowImportCommand : ControlCommand("import", "Import a pause set file from the host.") {
    private val file by option("--file", help = "The file on the host to import.").required()
    private val code by option("--code", help = "The code of a protected file.")
    private val mode by option("--mode", help = "combine, separate, or replace.").choice("combine", "separate", "replace").default("combine")
    private val turnOn by option("--turn-on", help = "Turn the imported schedules on from the result.").flag()

    override fun execute(session: Session): JsonElement {
        val source = Path.of(file).toAbsolutePath()
        if (!Files.isRegularFile(source)) throw ControlException(ErrorCode.USAGE, "--file $source is not a file.")
        val backend = session.backend()
        val files = transferFiles(session)
        files.place(source)
        TransferScreens.openMenu(backend, session.target(), "Import…")
        files.choose(backend, source.fileName.toString())
        code?.let { typed ->
            FlowSteps.run(
                backend,
                listOf(FlowSteps.waitFor(Query(text = "Enter the code")), FlowSteps.typeInto(typed, submit = false), FlowSteps.button("Open")),
            )
        }
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(text = "Choose how to import"))))
        val choice = when (mode) {
            "replace" -> FlowSteps.tap(Query(textContains = "Replace my pause sets"))
            "separate" -> FlowSteps.tap(Query(textContains = "Keep them separate"))
            else -> null
        }
        val confirm = if (mode == "replace") "Replace" else "Import"
        choice?.let { step -> FlowSteps.run(backend, listOf(step)) }
        val preview = FlowSteps.labels(backend).firstOrNull { label -> PREVIEW_PHRASES.any(label::contains) }
        // A refused import explains why and offers no button but Cancel.
        if (preview != null && REFUSALS.any(preview::startsWith)) {
            FlowSteps.run(backend, listOf(FlowSteps.button("Cancel")))
            files.cleanUp(source)
            return buildJsonObject {
                put("mode", mode)
                put("refused", true)
                put("preview", preview)
            }
        }
        FlowSteps.run(backend, listOf(FlowSteps.button(confirm), FlowSteps.waitFor(Query(text = "Imported"))))
        val result = FlowSteps.labels(backend)
        if (turnOn) {
            FlowSteps.run(
                backend,
                listOf(FlowSteps.button("Turn on imported schedules"), FlowSteps.waitFor(Query(text = "Imported schedules are on."))),
            )
        }
        FlowSteps.run(backend, listOf(FlowSteps.button("Done")))
        files.cleanUp(source)
        return buildJsonObject {
            put("mode", mode)
            preview?.let { put("preview", it) }
            put("result", result.filter { label -> RESULT_PHRASES.any(label::contains) }.joinToString(" "))
        }
    }

    private companion object {
        // Phrases of the line under the cards and of the result, whichever sentence comes first in them.
        val PREVIEW_PHRASES = listOf("Adds ", "Removes ", " becomes ", "switched off.", "Nothing new", "This would", "A running pause")
        val REFUSALS = listOf("This would", "A running pause")
        val RESULT_PHRASES = listOf("Pause sets changed", "Removed: ", "Schedules removed", " added.", "Imported schedules", "Nothing new")
    }
}

/** The app's own part of export and import, the same on every target. */
private object TransferScreens {
    fun openMenu(
        backend: Backend,
        target: Target,
        item: String,
    ) {
        val back = if (target.isApple) "Back to Pause sets" else "Back to pause sets"
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.button("Pause sets"),
                FlowSteps.button(back, optional = true, timeoutSeconds = SHORT_SECONDS),
                FlowSteps.tap(Query(text = "More actions for pause sets")),
                FlowSteps.tap(Query(text = item)),
            ),
        )
    }

    /** Turns on the code and reads it back, three groups of three as the dialog shows it. */
    fun protect(backend: Backend): String {
        FlowSteps.run(backend, listOf(FlowSteps.tap(Query(textContains = "Protect with a code"))))
        return FlowSteps.labels(backend).firstOrNull(CODE::matches)?.replace(" ", "")
            ?: throw ControlException(ErrorCode.ELEMENT_NOT_FOUND, "The export dialog shows no code after Protect with a code.")
    }

    private val CODE = Regex("[A-Z2-7]{3} [A-Z2-7]{3} [A-Z2-7]{3}")
    private const val SHORT_SECONDS = 3.0
}

/** Where a target's system panels put and find export files, and how the host reaches them. */
private interface TransferFiles {
    /** Removes an earlier export, so the one found after saving is this run's. */
    fun clear()

    fun save(backend: Backend)

    fun fetch(destination: Path)

    fun place(source: Path)

    /** Picks the file [place] put where the picker reaches it. */
    fun choose(
        backend: Backend,
        name: String,
    )

    fun cleanUp(source: Path)
}

private fun transferFiles(session: Session): TransferFiles {
    return when (session.target()) {
        Target.ANDROID -> AndroidTransferFiles(AndroidDevice(session.context, session.options.udid).also(AndroidDevice::requireReady))

        Target.DEVICE -> DeviceSession(session.context, Devicectl(session.context), session.options.udid).let { device ->
            IphoneTransferFiles(device.devicectl, device.udid(), session.context::log).also { files ->
                files.installHost(XcodeBuild(session.context))
            }
        }

        else -> throw ControlException(
            ErrorCode.UNSUPPORTED_ON_TARGET,
            "Export and import are driven on Android and the iPhone for now.",
            "The Mac and Linux file panels come next.",
        )
    }
}

/** Android's document picker, which opens in Documents on shared storage, where adb reads and writes too. */
private class AndroidTransferFiles(
    private val device: AndroidDevice,
) : TransferFiles {
    override fun clear() {
        device.shell("rm -f '$DOCUMENTS/$EXPORT_NAME'")
    }

    override fun save(backend: Backend) {
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(id = SAVE_BUTTON))))
        openDocuments(backend)
        FlowSteps.run(backend, listOf(FlowSteps.tap(Query(id = SAVE_BUTTON))))
    }

    /** The app writes after the picker closes, so the file shows up a moment later. */
    override fun fetch(destination: Path) {
        Files.createDirectories(destination.toAbsolutePath().parent)
        val deadline = System.nanoTime() + WRITE_WAIT_NANOS
        while (device.shell("test -s '$DOCUMENTS/$EXPORT_NAME'").exitCode != 0) {
            if (System.nanoTime() > deadline) throw ControlException(ErrorCode.COMMAND_FAILED, "No export was saved in $DOCUMENTS.")
            Thread.sleep(POLL_MILLIS)
        }
        device.adb("pull", "$DOCUMENTS/$EXPORT_NAME", destination.toString()).requireSuccess(ErrorCode.COMMAND_FAILED, "Pulling the export")
        device.shell("rm -f '$DOCUMENTS/$EXPORT_NAME'")
    }

    override fun place(source: Path) {
        device.adb(
            "push",
            source.toString(),
            "$DOCUMENTS/${source.fileName}",
        ).requireSuccess(ErrorCode.COMMAND_FAILED, "Placing the file in Documents")
    }

    override fun choose(
        backend: Backend,
        name: String,
    ) {
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(id = HEADER))))
        openDocuments(backend)
        FlowSteps.run(backend, listOf(FlowSteps.tap(Query(textContains = name))))
    }

    override fun cleanUp(source: Path) {
        device.shell("rm -f '$DOCUMENTS/${source.fileName}'")
    }

    /**
     * The picker opens where it was used last. From anywhere, this phone's storage, reached through its breadcrumb
     * or Show roots, and then its Documents folder bring it to the folder adb reads and writes.
     */
    private fun openDocuments(backend: Backend) {
        if (FlowSteps.labels(backend).contains(IN_DOCUMENTS)) return
        val phone = device.shell("settings get global device_name").stdout.trim()
        val crumb = Query(id = BREADCRUMB, text = phone)
        val steps = if (FlowSteps.nodes(backend).any { node -> node.id == BREADCRUMB && node.label == phone }) {
            listOf(FlowSteps.tap(crumb))
        } else {
            listOf(FlowSteps.tap(Query(text = "Show roots")), FlowSteps.tap(Query(text = phone)))
        }
        FlowSteps.run(
            backend,
            steps + listOf(FlowSteps.tap(Query(id = FOLDER, text = "Documents")), FlowSteps.waitFor(Query(text = IN_DOCUMENTS))),
        )
    }

    private companion object {
        const val DOCUMENTS = "/sdcard/Documents"
        const val IN_DOCUMENTS = "Files in Documents"
        const val BREADCRUMB = "com.google.android.documentsui:id/breadcrumb_text"
        const val HEADER = "com.google.android.documentsui:id/header_title"
        const val FOLDER = "com.google.android.documentsui:id/item_root"
        const val SAVE_BUTTON = "android:id/button1"
        const val WRITE_WAIT_NANOS = 10_000_000_000L
        const val POLL_MILLIS = 250L
    }
}

/**
 * The iPhone's share sheet and document picker. The host reaches files through the driver host app, whose
 * Documents folder shows under On My iPhone because it declares file sharing; Posato itself declares none.
 */
private class IphoneTransferFiles(
    private val devicectl: Devicectl,
    private val udid: String,
    private val log: (String) -> Unit,
) : TransferFiles {
    /** Installs the driver host, whose folder the files pass through; installing again keeps that folder. */
    fun installHost(xcodeBuild: XcodeBuild) {
        if (xcodeBuild.driverState(Target.DEVICE) != XcodeBuild.DriverState.FRESH) xcodeBuild.buildDriver(Target.DEVICE, udid)
        val host = xcodeBuild.driverDerivedData(Target.DEVICE).resolve("Build/Products/Debug-iphoneos/$HOST_FOLDER.app")
        devicectl.install(udid, host)
    }

    /**
     * Clears the host's folder, so Files never asks to replace an older export and the one fetched is new. A note
     * stays in it, since Files hides an app's empty folder.
     */
    override fun clear() {
        val folder = Files.createDirectories(Files.createTempDirectory("posato-control-transfer").resolve(HOST_DOCUMENTS))
        Files.writeString(folder.resolve("posato-control.txt"), "Files that posato-control passes to and from Posato.\n")
        devicectl.copyAppData(udid, HOST_BUNDLE, toDevice = true, folder, HOST_DOCUMENTS, replaceFolder = true)
    }

    /**
     * Save to Files runs in its own extension, so its sheet is addressed by that extension's bundle, and a query
     * binds to that process when its scenario starts, so the sheet is read in a scenario after the tap. The share
     * sheet swallows the first tap on Save to Files at times, so a second one follows when no sheet came up.
     */
    override fun save(backend: Backend) {
        val sheet = FlowSteps.waitFor(Query(role = FlowSteps.ROLE_BUTTON, text = "Save", scope = SAVE_SHEET), timeoutSeconds = SHEET_SECONDS)
        FlowSteps.run(backend, listOf(FlowSteps.waitFor(Query(text = "Save to Files")), FlowSteps.tap(Query(text = "Save to Files"))))
        try {
            FlowSteps.run(backend, listOf(sheet))
        } catch (missing: ControlException) {
            log("Save to Files did not open on the first tap: ${missing.message}")
            FlowSteps.run(backend, listOf(FlowSteps.tap(Query(text = "Save to Files"))))
            FlowSteps.run(backend, listOf(sheet))
        }
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.tap(
                    Query(role = FlowSteps.ROLE_BUTTON, text = "Back", scope = SAVE_SHEET),
                    optional = true,
                    timeoutSeconds = SHORT_SECONDS,
                ),
                FlowSteps.tap(Query(text = "On My iPhone", scope = SAVE_SHEET)),
                FlowSteps.tap(Query(textContains = HOST_FOLDER.take(FOLDER_PREFIX), scope = SAVE_SHEET)),
            ),
        )
        // Save pressed while the folder slides in is lost, so it waits for the folder in a scenario of its own.
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.waitFor(Query(textContains = HOST_FOLDER.take(FOLDER_PREFIX), scope = SAVE_SHEET)),
                FlowSteps.sleep(SETTLE_SECONDS),
                FlowSteps.tap(Query(role = FlowSteps.ROLE_BUTTON, text = "Save", scope = SAVE_SHEET)),
            ),
        )
    }

    override fun fetch(destination: Path) {
        Files.createDirectories(destination.toAbsolutePath().parent)
        devicectl.copyAppData(udid, HOST_BUNDLE, toDevice = false, destination, "$HOST_DOCUMENTS/$EXPORT_NAME")
    }

    override fun place(source: Path) {
        devicectl.copyAppData(udid, HOST_BUNDLE, toDevice = true, source, "$HOST_DOCUMENTS/${source.fileName}")
    }

    override fun choose(
        backend: Backend,
        name: String,
    ) {
        FlowSteps.run(
            backend,
            listOf(
                FlowSteps.tap(Query(text = "Browse"), optional = true, timeoutSeconds = SHORT_SECONDS),
                FlowSteps.tap(Query(text = "On My iPhone"), optional = true, timeoutSeconds = SHORT_SECONDS),
                FlowSteps.tap(Query(text = HOST_FOLDER)),
                FlowSteps.tap(Query(textContains = name.removeSuffix(".posato"))),
            ),
        )
    }

    override fun cleanUp(source: Path) = Unit

    private companion object {
        const val HOST_BUNDLE = "app.posato.ios.driverhost"
        const val HOST_FOLDER = "PosatoDriverHost"
        const val SAVE_SHEET = "com.apple.DocumentManagerUICore.SaveToFiles"

        // Files wraps the folder name, so its label may break inside it.
        const val FOLDER_PREFIX = 12
        const val SHORT_SECONDS = 4.0

        const val SHEET_SECONDS = 8.0
        const val SETTLE_SECONDS = 1.0
        const val HOST_DOCUMENTS = "Documents"
    }
}

private const val EXPORT_NAME = "Posato pause sets.posato"
