package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** One on-screen guest window, as the window server reports it. */
@Serializable
data class GuestWindow(
    val owner: String,
    val layer: Long,
    val title: String = "",
)

/** A system dialog or notice, named by the process that owns its window. */
@Serializable
data class GuestDialog(
    val kind: String,
    val owner: String,
    val title: String,
)

/**
 * The processes whose windows are system dialogs, by kind. Recognized text cannot tell an administrator dialog from
 * a notice that merely says "allow this" (observed in `RELEASE-004`), but the owning process can.
 */
private val DIALOG_OWNERS = mapOf(
    "SecurityAgent" to "admin",
    "CoreServicesUIAgent" to "gatekeeper",
    "UserNotificationCenter" to "system-alert",
    "universalAccessAuthWarn" to "accessibility",
)

private const val NOTIFICATION_OWNER = "Notification Center"

/** The system dialogs and notification banners among [windows]; the Notification Center widgets sit below layer 0. */
private fun dialogsAmong(windows: List<GuestWindow>): List<GuestDialog> = windows.mapNotNull { window ->
    val kind = DIALOG_OWNERS[window.owner] ?: if (window.owner == NOTIFICATION_OWNER && window.layer >= 0) "notification" else null
    kind?.let { GuestDialog(it, window.owner, window.title) }
}

private fun parseGuestWindows(json: String): List<GuestWindow> = ControlJson.lenient.decodeFromString(ListSerializer(GuestWindow.serializer()), json)

/** Lists the guest's system dialogs from the window server, which needs neither Automation nor text recognition. */
class GuestDialogs(
    private val context: RunContext,
) {
    fun list(line: VmLine): List<GuestDialog> {
        val output = Tart(context).exec(line.cloneName, "osascript -l JavaScript -", stdin = WINDOW_LIST_SCRIPT)
        if (!output.succeeded) {
            throw ControlException(ErrorCode.COMMAND_FAILED, "Listing the guest's windows failed: ${output.stderr.trim().take(ERROR_EXCERPT)}")
        }
        return dialogsAmong(parseGuestWindows(output.stdout.trim()))
    }

    private companion object {
        const val ERROR_EXCERPT = 200
        val WINDOW_LIST_SCRIPT =
            """
            ObjC.import('CoreGraphics');
            var info = ObjC.castRefToObject(${'$'}.CGWindowListCopyWindowInfo(${'$'}.kCGWindowListOptionOnScreenOnly | ${'$'}.kCGWindowListExcludeDesktopElements, 0));
            JSON.stringify(ObjC.deepUnwrap(info).map(function (w) {
                return { owner: w.kCGWindowOwnerName || '', layer: w.kCGWindowLayer, title: w.kCGWindowName || '' };
            }));
            """.trimIndent()
    }
}
