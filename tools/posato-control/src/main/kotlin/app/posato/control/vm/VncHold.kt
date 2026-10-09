package app.posato.control.vm

import app.posato.control.core.RunContext
import java.nio.file.Files

/**
 * Virtualization's VNC server can take Tart down when a client connects after a few minutes without one: Tart stops
 * on an assertion in `-[_VZVirtualMachineAccessor addAccessorObserver:]`, called from
 * `-[_VZVNCServer _setupVirtualMachineAccessor]` (`observed` 2026-10-07, macOS 27.0.1, Tart 2.37.0). The driver
 * connects afresh for every capture, so a capture after an idle stretch, such as the iCloud Keychain check after a
 * two-minute wait for a link, ended the clone. One idle connection held for the clone's whole life keeps every later
 * capture from being the first one: on one clone, a capture after each of eight 150 s idle stretches passed with it,
 * while without it the second such capture crashed Tart.
 */
internal class VncHold(
    private val context: RunContext,
    private val line: VmLine,
) {
    /**
     * Starts the holder as a process of its own, which ends by itself once the clone no longer runs, and reports
     * `started`, or `skipped` when this checkout has no installed driver to run it with.
     */
    fun start(): String {
        val launcher = context.layout.root.resolve(LAUNCHER)
        if (!Files.isExecutable(launcher)) {
            context.log("Not holding a VNC connection to ${line.cloneName}: $launcher is missing.")
            return "skipped"
        }
        context.subprocess.startDetached(
            listOf(launcher.toString(), "vm", "vnc-hold", "--line", line.id),
            vmDirectory(line).resolve(HOLD_LOG),
        )
        return "started"
    }

    /** Holds one idle connection until the clone stops running or its server closes it, and returns how long it held it. */
    fun hold(): Long {
        val lifecycle = VmLifecycle(context)
        val endpoint = vmEndpoint(line, ENDPOINT_TIMEOUT_MS)
        val started = System.currentTimeMillis()
        // A clone created again under the same name runs a new server, which this connection never reaches.
        VncClient.connect(endpoint.host, endpoint.port, endpoint.password).use { client ->
            var open = true
            while (open && lifecycle.running(line)) open = !client.closesWithin(POLL_MS)
        }
        return System.currentTimeMillis() - started
    }

    private companion object {
        const val LAUNCHER = "tools/posato-control/build/install/posato-control/bin/posato-control"
        const val HOLD_LOG = "vnc-hold.log"
        const val ENDPOINT_TIMEOUT_MS = 60_000L
        const val POLL_MS = 60_000
    }
}
