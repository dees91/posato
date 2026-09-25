package app.posato.control.core

import java.util.concurrent.TimeUnit

/**
 * Desktop verification never runs the application on the maintainer's own Mac: a development package shares the
 * installed Posato's data, Keychain items, helper registration, and iCloud state, so driving it on the host changes
 * the maintainer's real installation. Only commands that build or inspect the toolchain may address the desktop
 * target there; everything else runs inside a Tart guest through `--vm`.
 */
fun refuseHostDesktop(
    target: Target?,
    hostAllowed: Boolean,
    inVirtualMachine: () -> Boolean,
) {
    if (target != Target.DESKTOP || hostAllowed || inVirtualMachine()) return
    throw ControlException(
        ErrorCode.DESKTOP_HOST_REFUSED,
        "The desktop application is never driven on the host Mac.",
        "Run it in a Tart VM: `posato-control vm create --line primary`, then add `--vm primary` to this command.",
    )
}

/** True inside a virtual machine: macOS sets `kern.hv_vmm_present` to 1 in a guest and 0 on physical hardware. */
fun runsInVirtualMachine(): Boolean {
    val process = ProcessBuilder("/usr/sbin/sysctl", "-n", "kern.hv_vmm_present").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return process.waitFor(SYSCTL_TIMEOUT_SECONDS, TimeUnit.SECONDS) && output.trim() == "1"
}

private const val SYSCTL_TIMEOUT_SECONDS = 5L
