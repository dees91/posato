package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext

/**
 * LaunchServices in a clone. A second registered Posato bundle, such as the synced development package, a golden
 * image's stale entry, a mounted image, or a translocated copy, can take over launch resolution for the helper and
 * the update installer, so candidate runs require that only the installed candidate is known.
 */
class GuestRegistrations(
    private val context: RunContext
) {
    private val tart = Tart(context)

    /** Unregisters every other Posato bundle and then requires that none is left; returns the remaining Posato bundles. */
    fun requireSingleBundle(line: VmLine): List<String> {
        retireOtherBundles(line)
        val registrations = registrations(line)
        val foreign = foreignRegistrations(registrations, INSTALLED_APPLICATION)
        if (foreign.isNotEmpty()) {
            throw ControlException(
                ErrorCode.INSTALL_FAILED,
                "LaunchServices still knows other Posato bundles: ${foreign.joinToString(", ")}.",
                "Destroy the clone and install the candidate into a fresh one.",
            )
        }
        return registrations.filter { isPosatoBundle(it) }
    }

    /** A path registered twice needs one `lsregister -u` per entry, so passes repeat until none is left. */
    fun retireOtherBundles(line: VmLine): List<String> {
        val retired = mutableListOf<String>()
        repeat(RETIRE_PASSES) {
            val foreign = foreignRegistrations(registrations(line), INSTALLED_APPLICATION)
            if (foreign.isEmpty()) return retired
            tart.exec(
                line.cloneName,
                "while IFS= read -r bundle; do $LSREGISTER -u \"\$bundle\"; done",
                stdin = foreign.joinToString("\n") + "\n",
            )
            retired += foreign.filterNot { it in retired }
        }
        return retired
    }

    private fun registrations(line: VmLine): List<String> =
        tart.exec(line.cloneName, "$LSREGISTER -dump | sed -n 's/^path: *\\(.*\\.app\\) (0x[0-9a-f]*)\$/\\1/p' | sort -u")
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Listing LaunchServices registrations")
            .stdout.lines().filter { it.isNotBlank() }

    private companion object {
        const val LSREGISTER = "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
        const val RETIRE_PASSES = 5
    }
}

/** A registered application bundle that belongs to Posato: the application itself or one of its nested helpers. */
internal fun isPosatoBundle(path: String): Boolean = path.endsWith(".app") && path.substringAfterLast('/').startsWith("Posato")

/** Registered Posato bundles outside [installed], such as a development package or the golden image's stale copies. */
internal fun foreignRegistrations(
    registrations: List<String>,
    installed: String
): List<String> = registrations.filter { isPosatoBundle(it) && it != installed && !it.startsWith("$installed/") }
