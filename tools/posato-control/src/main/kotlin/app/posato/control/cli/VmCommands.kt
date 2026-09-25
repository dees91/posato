package app.posato.control.cli

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.readKeychainSecret
import app.posato.control.vm.CandidateInstall
import app.posato.control.vm.CandidateInstallation
import app.posato.control.vm.GuestICloud
import app.posato.control.vm.GuestPrompt
import app.posato.control.vm.VmLifecycle
import app.posato.control.vm.VmLine
import app.posato.control.vm.VmPrompts
import app.posato.control.vm.vmAdminPassword
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path

class VmCommand : CliktCommand(name = "vm") {
    override fun help(context: Context): String =
        "Tart macOS guests for unattended desktop verification: create and destroy the per-run clone of a golden VM, " +
            "copy the staged package or install a notarized candidate into it, and answer system dialogs over VNC. Desktop commands reach the guest with --vm primary|peer|legacy."

    override fun run() = Unit
}

class VmCreateCommand : ControlCommand("create", "Clone the line's golden VM, boot it headless, and copy the staged package and driver into it.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val created = VmLifecycle(session.context).create(line)
        // A paused iCloud Keychain only shows much later as a workspace key that never arrives, so report it now.
        val iCloud = runCatching { GuestICloud(session.context).check(line, ICLOUD_CHECK_TIMEOUT_MS).id }.getOrDefault("unknown")
        return JsonObject(created + ("iCloudKeychain" to JsonPrimitive(iCloud)))
    }
}

class VmSyncCommand : ControlCommand("sync", "Copy the freshly staged package and the driver into the running clone.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        VmLifecycle(session.context).sync(line)
        return buildJsonObject { put("vm", line.cloneName) }
    }
}

class VmInstallCommand :
    ControlCommand(
        "install",
        "Install a notarized candidate DMG into /Applications as a person would and point the guest's desktop commands at it.",
    ) {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val dmg by option("--dmg", help = "Host path of the notarized candidate disk image.").required()
    private val applicationLabel by option("--app-label", help = "The application's label in the image window.").default("Posato")
    private val applicationsLabel by option("--applications-label", help = "The Applications link's label in the image window.")
        .default("/Applications")
    private val timeoutSeconds by option("--timeout-seconds", help = "How long each step may take.").long().default(INSTALL_TIMEOUT_SECONDS)
    private val replace by option(
        "--replace",
        help = "Move an installed Posato to the Trash first, as Finder's Replace does, for the manual move between releases.",
    ).flag()

    override fun execute(session: Session): JsonElement {
        val installation = CandidateInstall(session.context).install(
            VmLine.parse(lineOption),
            Path.of(dmg),
            applicationLabel,
            applicationsLabel,
            timeoutSeconds * MILLIS_PER_SECOND,
            replace,
        )
        return ControlJson.pretty.encodeToJsonElement(CandidateInstallation.serializer(), installation)
    }
}

class VmDestroyCommand : ControlCommand("destroy", "Shut the clone down from inside the guest and delete it.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        VmLifecycle(session.context).destroy(line)
        return buildJsonObject { put("vm", line.cloneName) }
    }
}

class VmPromptCommand : ControlCommand("prompt", "Answer a system dialog over VNC: admin, background, gatekeeper, or picker-bypass.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val kind by argument(
        help = "admin | background | toggle | account-password | mac-password | device-passcode | gatekeeper | picker-bypass",
    )
    private val row by option("--row", help = "toggle only: the System Settings row whose switch to turn on.")
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the dialog.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        val prompt = GuestPrompt.parse(kind)
        VmPrompts(session.context).answer(VmLine.parse(lineOption), prompt, timeoutSeconds * MILLIS_PER_SECOND, row)
        return buildJsonObject { put("prompt", prompt.id) }
    }
}

class VmClickCommand : ControlCommand("click", "Click text on the guest screen, located by text recognition.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val text by option("--text", help = "Text to find.").default("")
    private val exact by option("--exact", help = "Match the whole recognized line.").flag()
    private val index by option("--index", help = "The nth match, 0-based.").int().default(0)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for the text.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        VmPrompts(session.context).click(VmLine.parse(lineOption), text, exact, index, timeoutSeconds * MILLIS_PER_SECOND)
        return buildJsonObject { put("clicked", text) }
    }
}

class VmDragCommand : ControlCommand("drag", "Drag one recognized label onto another on the guest screen, such as an app icon onto Applications.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val from by option("--from", help = "Exact label of the item to drag.").required()
    private val fromIndex by option("--from-index", help = "The nth --from match from the top, 0-based.").int().default(0)
    private val to by option("--to", help = "Exact label of the drop target.").required()
    private val toIndex by option("--to-index", help = "The nth --to match from the top, 0-based.").int().default(0)
    private val timeoutSeconds by option("--timeout-seconds", help = "How long to wait for both labels.").long().default(DEFAULT_TIMEOUT_SECONDS)

    override fun execute(session: Session): JsonElement {
        VmPrompts(session.context).drag(VmLine.parse(lineOption), from, fromIndex, to, toIndex, timeoutSeconds * MILLIS_PER_SECOND)
        return buildJsonObject {
            put("dragged", from)
            put("onto", to)
        }
    }
}

class VmBootCommand :
    ControlCommand("boot", "Boot the line's existing VM headless without cloning, to prepare a golden image under the clone's name.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement = VmLifecycle(session.context).boot(VmLine.parse(lineOption))
}

class VmShutdownCommand : ControlCommand("shutdown", "Shut the line's VM down from inside the guest and keep it.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val forced = VmLifecycle(session.context).shutdown(line)
        return buildJsonObject {
            put("vm", line.cloneName)
            put("forced", forced)
        }
    }
}

class VmTypeCommand : ControlCommand("type", "Type text, or a password from the Keychain, into the focused guest field over VNC.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val text by option("--text", help = "Literal text to type.")
    private val secret by option(
        "--secret",
        help = "admin, account, or phone: type the guest administrator password, the test Apple Account password, " +
            "or the account's trusted phone number from the Keychain.",
    )
    private val stripPrefix by option("--strip-prefix", help = "Drop this prefix from the typed value, such as a country code.")

    override fun execute(session: Session): JsonElement {
        val value = when {
            secret == "admin" && text == null -> vmAdminPassword(session.context)

            secret == "account" && text == null -> readKeychainSecret(
                session.context,
                ConfigurationKey.VM_ACCOUNT_KEYCHAIN_SERVICE,
                ConfigurationKey.VM_ACCOUNT_KEYCHAIN_ACCOUNT,
                "the test Apple Account password",
            )

            secret == "phone" && text == null -> readKeychainSecret(
                session.context,
                ConfigurationKey.VM_ACCOUNT_PHONE_KEYCHAIN_SERVICE,
                ConfigurationKey.VM_ACCOUNT_KEYCHAIN_ACCOUNT,
                "the test Apple Account's trusted phone number",
            )

            secret == null && text != null -> checkNotNull(text)

            else -> throw ControlException(ErrorCode.USAGE, "Pass either --text or --secret admin|account|phone.")
        }.let { typed -> stripPrefix?.let(typed::removePrefix) ?: typed }
        VmPrompts(session.context).type(VmLine.parse(lineOption), value)
        return buildJsonObject { put("typed", if (secret != null) "<secret>" else value) }
    }
}

class VmPressCommand : ControlCommand("press", "Press a key or chord in the guest over VNC, such as return or cmd-q.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val key by argument(help = "Key or chord, for example return, escape, cmd-q.")

    override fun execute(session: Session): JsonElement {
        VmPrompts(session.context).press(VmLine.parse(lineOption), key)
        return buildJsonObject { put("pressed", key) }
    }
}

class VmScreenshotCommand : ControlCommand("screenshot", "Capture the whole guest screen over VNC into the run directory.") {
    private val lineOption by option("--line", help = "VM line: primary, peer, or legacy.").default(VmLine.PRIMARY.id)
    private val name by option("--name", help = "Artifact name without extension.").default("guest-screen")

    override fun execute(session: Session): JsonElement {
        val path = VmPrompts(session.context).screenshot(VmLine.parse(lineOption), name)
        return buildJsonObject { put("path", session.layout.relativize(path)) }
    }
}

private const val DEFAULT_TIMEOUT_SECONDS = 60L
private const val ICLOUD_CHECK_TIMEOUT_MS = 60_000L
private const val INSTALL_TIMEOUT_SECONDS = 120L
private const val MILLIS_PER_SECOND = 1_000L
