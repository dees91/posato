package app.posato.control.cli

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RepoLayout
import app.posato.control.vm.GUEST_ROOT
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/**
 * Runs a verification seam of a development package with `-PposatoMacOsVerificationSeams=true` (ADR 0007
 * amendment of 2026-10-09): `status` reads whether this clone's Mac is local-only and whether the zone's anchor
 * exists; `delete-zone` deletes the test Apple Account's Posato zone. Before deleting it checks, itself, that no
 * other Tart VM exists than this clone and the configured golden images, that the guest is signed in to the
 * configured test account, and that the anchor is absent; the companion then refuses outside Development.
 */
class VmSyncFixtureCommand :
    ControlCommand(
        "sync-fixture",
        "Verification seams of a seams-enabled development package: status, or delete-zone for the test Apple " +
            "Account after its workspace is removed (SYNC-021).",
    ) {
    private val lineOption by option("--line", help = "VM line; the iCloud fixture uses primary.").default(VmLine.PRIMARY.id)
    private val action by argument(help = "status or delete-zone").choice(STATUS, DELETE_ZONE)

    override fun execute(session: Session): JsonElement {
        val line = VmLine.parse(lineOption)
        val tart = Tart(session.context)
        if (action == DELETE_ZONE) {
            requireOnlyThisClone(session, tart, line)
            requireTestAccount(session, tart, line)
        }
        val status = seam(tart, line, STATUS)
        if (action == STATUS) return withLine(line, status)
        if (status.field("outcome") == "local-only" && status.field("anchor") == ZONE_ABSENT) return deleted(line, ZONE_ALREADY_ABSENT)
        if (status.field("outcome") != "local-only" || status.field("anchor") != "absent") {
            throw ControlException(
                ErrorCode.PRECONDITION_NOT_MET,
                "The zone may be deleted only while this Mac is local-only and the anchor is absent; status was $status.",
                "Remove the workspace on every device first, then run vm sync-fixture status.",
            )
        }
        val deletion = seam(tart, line, DELETE_ZONE)
        return when (deletion.field("outcome")) {
            "zone-deleted" -> deleted(line, "zone-deleted")
            ZONE_ALREADY_ABSENT -> deleted(line, ZONE_ALREADY_ABSENT)
            else -> throw ControlException(ErrorCode.COMMAND_FAILED, "The zone deletion did not finish: $deletion.")
        }
    }

    /**
     * Always names the earliest next link, also when the zone was already absent: a lost reply or a rerun after a
     * deletion must not invite an immediate link inside the purge window.
     */
    private fun deleted(
        line: VmLine,
        outcome: String,
    ): JsonElement {
        val now = Instant.now()
        return buildJsonObject {
            put("vm", line.cloneName)
            put("outcome", outcome)
            put("checkedAt", now.toString())
            put("nextLinkNotBefore", now.plusSeconds(WAIT_AFTER_DELETE_SECONDS).toString())
        }
    }

    private fun requireOnlyThisClone(
        session: Session,
        tart: Tart,
        line: VmLine,
    ) {
        val goldens = VmLine.entries.mapNotNull { vmLine -> session.context.configuration.value(vmLine.goldenKey) }.toSet()
        val others = tart.list().map { vm -> vm.name }.filterNot { name -> name == line.cloneName || name in goldens }
        if (others.isNotEmpty()) {
            throw ControlException(
                ErrorCode.PRECONDITION_NOT_MET,
                "Other Tart VMs exist: ${others.joinToString()}. Zone deletion needs this clone and the golden images only.",
                "Destroy the other clones first, or wait for the sessions that own them.",
            )
        }
    }

    private fun requireTestAccount(
        session: Session,
        tart: Tart,
        line: VmLine,
    ) {
        val expected = session.context.configuration.value(ConfigurationKey.VM_ACCOUNT_KEYCHAIN_ACCOUNT)
            ?: throw ControlException(ErrorCode.PRECONDITION_NOT_MET, "posato.vm.accountKeychainAccount names no test Apple Account.")
        val accounts = tart.exec(line.cloneName, "/usr/bin/defaults read MobileMeAccounts Accounts 2>/dev/null").stdout
        val signedIn = ACCOUNT_ID.findAll(accounts).map { match -> match.groupValues[1] }.toList()
        if (signedIn != listOf(expected)) {
            throw ControlException(
                ErrorCode.PRECONDITION_NOT_MET,
                "The guest is not signed in to the configured test Apple Account alone.",
                "Zone deletion runs only on the test Apple Account; check the golden VM's iCloud sign-in.",
            )
        }
    }

    /** Quits Posato in the guest, then runs one seam with the launch argument and reads its JSON line. */
    private fun seam(
        tart: Tart,
        line: VmLine,
        command: String,
    ): JsonObject {
        val application = "app=\$(cat \"$GUEST_ROOT/$INSTALLED_MARKER\" 2>/dev/null || echo \"$GUEST_ROOT/$STAGED_APPLICATION\")"
        val script = "$application; /usr/bin/pkill -TERM -x Posato; " +
            "i=0; while /usr/bin/pgrep -x Posato >/dev/null && [ \$i -lt 30 ]; do sleep 1; i=\$((i+1)); done; " +
            "\"\$app/Contents/MacOS/Posato\" --posato-verification $command"
        val output = tart.exec(line.cloneName, script, timeout = SEAM_TIMEOUT)
        val json = output.stdout.lines().lastOrNull { text -> text.startsWith("{") }
            ?: throw ControlException(
                ErrorCode.COMMAND_FAILED,
                "The verification seam '$command' printed no result (exit ${output.exitCode}).",
                "Build the package with posato-control build --verification-seams and vm sync it.",
            )
        val result = Json.parseToJsonElement(json) as JsonObject
        if (result.field("outcome") == "inert") {
            throw ControlException(
                ErrorCode.PRECONDITION_NOT_MET,
                "The package in ${line.cloneName} has no verification seams.",
                "Build with posato-control build --verification-seams, then vm sync.",
            )
        }
        return result
    }

    private fun withLine(
        line: VmLine,
        result: JsonObject,
    ): JsonElement {
        return JsonObject(mapOf("vm" to Json.parseToJsonElement("\"${line.cloneName}\"")) + result)
    }

    private fun JsonObject.field(name: String): String? = this[name]?.jsonPrimitive?.content

    private companion object {
        const val STATUS = "status"
        const val DELETE_ZONE = "delete-zone"
        const val ZONE_ABSENT = "zone-absent"
        const val ZONE_ALREADY_ABSENT = "zone-already-absent"
        const val INSTALLED_MARKER = "build/verification/desktop-application"
        const val STAGED_APPLICATION = "desktopApp/build/compose/binaries/main/${RepoLayout.STAGED_PACKAGE_DIRECTORY}/Posato.app"
        const val WAIT_AFTER_DELETE_SECONDS = 15L * 60L
        val SEAM_TIMEOUT: Duration = Duration.ofMinutes(3)
        val ACCOUNT_ID = Regex("AccountID\\s*=\\s*\"?([^\";\\s]+)\"?;")
    }
}
