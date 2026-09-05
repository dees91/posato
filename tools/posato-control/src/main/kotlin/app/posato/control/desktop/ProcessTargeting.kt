package app.posato.control.desktop

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.nio.file.Path

/**
 * Decides which process an element command addresses.
 *
 * The tracked application must be running before any process is addressable, and only a process whose executable
 * lives inside the staged bundle can be selected. Every other outcome is a precondition failure, never a silent
 * fallback to the tracked application.
 */
object ProcessTargeting {
    fun resolve(
        selector: String?,
        trackedPid: Long?,
        contained: () -> List<ProcessEntry>
    ): Long {
        val running = trackedPid ?: throw ControlException(
            ErrorCode.APP_NOT_RUNNING,
            "No tracked desktop process is running.",
            "Run `posato-control launch -t desktop` first.",
        )
        if (selector == null) return running
        val candidates = contained()
        return if (selector.isNotEmpty() && selector.all { it in '0'..'9' }) {
            selectByPid(selector, candidates)
        } else {
            selectByName(selector, candidates)
        }
    }

    private fun selectByPid(
        selector: String,
        contained: List<ProcessEntry>
    ): Long {
        val pid = selector.toLongOrNull() ?: throw refusal("'$selector' is not a process id.", contained)
        if (contained.none { it.pid == pid }) {
            throw refusal("No running process with id $pid runs an executable inside the staged Posato.app.", contained)
        }
        return pid
    }

    private fun selectByName(
        selector: String,
        contained: List<ProcessEntry>
    ): Long {
        val matches = contained.filter { executableName(it) == selector }
        if (matches.isEmpty()) throw refusal("No process inside the staged Posato.app runs '$selector'.", contained)
        if (matches.size > 1) {
            throw refusal("'$selector' matches ${matches.size} processes (${matches.joinToString(", ") { it.pid.toString() }}).", contained)
        }
        return matches.single().pid
    }

    private fun refusal(
        detail: String,
        contained: List<ProcessEntry>
    ): ControlException {
        val names = contained.map { executableName(it) }.distinct().sorted()
        val hint = if (names.isEmpty()) {
            "Only a process inside the staged Posato.app is addressable, and none is running."
        } else {
            "Addressable now: ${names.joinToString(", ")}. Pass one of those names or its process id."
        }
        return ControlException(ErrorCode.PROCESS_NOT_ALLOWED, detail, hint)
    }

    private fun executableName(entry: ProcessEntry): String = Path.of(entry.command).fileName.toString()
}
