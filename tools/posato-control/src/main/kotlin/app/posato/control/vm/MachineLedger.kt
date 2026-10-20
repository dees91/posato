package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * The host's `machine-ledger`, which keeps leases on large artifacts and on memory so that sessions of every project
 * share one free space reserve and one memory budget. `vm create` and `linux create` claim their clone before cloning,
 * with the memory the clone will take, and `vm destroy` and `linux destroy` release it. The ledger is cooperative:
 * a host without it, or one whose call fails or hangs, still creates and destroys clones, and only a claim the ledger
 * refuses, below its disk reserve or beyond its memory budget, stops a clone.
 */
internal class MachineLedger(
    private val binary: Path?,
    private val log: (String) -> Unit,
    private val run: (List<String>) -> ProcessOutput,
) {
    /**
     * Claims [clone], about to be created in [cloneDirectory] by [worktree] with [command], and the [memoryMib] its
     * guest will take once it runs, when known. [belowReserve], for a clone created with `--allow-low-disk`, records
     * the lease even below the disk reserve; nothing overrides the memory budget.
     */
    fun claim(
        clone: String,
        cloneDirectory: Path,
        worktree: Path,
        belowReserve: Boolean,
        memoryMib: Int? = null,
        command: String = "vm create",
    ) {
        if (binary == null) {
            log("machine-ledger is not installed; creating $clone without a lease.")
            return
        }
        val claim = buildList {
            addAll(listOf(binary.toString(), "claim", "--owner", OWNER, "--kind", KIND, "--id", leaseId(clone)))
            addAll(listOf("--path", cloneDirectory.toString(), "--size", CLONE_GROWTH, "--ttl", LEASE_TTL))
            addAll(listOf("--note", "posato-control $command in $worktree"))
            memoryMib?.let { addAll(listOf("--memory", "${it}M")) }
            if (belowReserve) add("--below-reserve")
        }
        val output = call(claim, "claim") ?: return
        when (output.exitCode) {
            0 -> log(output.stdout.trim())
            REFUSED -> throw refusal(clone, output.stderr)
            REFUSED_MEMORY -> throw memoryRefusal(clone, output.stderr)
            else -> log("machine-ledger claim exited with ${output.exitCode}; creating $clone without a lease: ${output.stderr.trim()}")
        }
    }

    /** Ends [clone]'s lease once the clone is gone; a missing binary or a failed call changes nothing. */
    fun release(clone: String) {
        if (binary == null) return
        call(listOf(binary.toString(), "release", leaseId(clone)), "release")
    }

    private fun call(
        command: List<String>,
        what: String
    ): ProcessOutput? = try {
        run(command)
    } catch (failure: ControlException) {
        log("machine-ledger $what failed, continuing without it: ${failure.message}")
        null
    } catch (failure: IOException) {
        log("machine-ledger $what failed, continuing without it: ${failure.message}")
        null
    }

    /** The ledger's own reason, which lists what could be freed, and how to go on. */
    private fun refusal(
        clone: String,
        stderr: String
    ) = ControlException(
        ErrorCode.DISK_SPACE_LOW,
        stderr.trim().ifEmpty { "machine-ledger refused the lease ${leaseId(clone)} below its reserve." },
        "Ask the maintainer before freeing anything: `machine-ledger plan` lists what may go, and " +
            "`machine-ledger clean --validate` checks a selection. Pass --allow-low-disk to claim below the reserve " +
            "and create the clone anyway.",
    )

    /** The ledger's reason lists what runs and its memory; only stopping something frees it, so no flag goes on. */
    private fun memoryRefusal(
        clone: String,
        stderr: String
    ) = ControlException(
        ErrorCode.HOST_MEMORY_COMMITTED,
        stderr.trim().ifEmpty { "machine-ledger refused the lease ${leaseId(clone)}: the host's memory is committed." },
        "Ask the maintainer what to stop: `machine-ledger host` lists the running VMs, emulators, simulators and Colima " +
            "with their memory. Do not work around the refusal.",
    )

    companion object {
        private const val OWNER = "Polyglot/posato"
        private const val KIND = "tart-vm"

        /**
         * What a clone is expected to add to the disk: its copy-on-write disk grows by about 2.5 GB at boot alone, then
         * by the package and driver copies and what the guest writes. The rest of a run, the desktop build and
         * `./gradlew quality`, is already covered by [MIN_FREE_DISK_BYTES], which `vm create` checks first; claiming it
         * here too would count it twice against the ledger's reserve.
         */
        private const val CLONE_GROWTH = "5G"

        /** A run rarely keeps its clone a day; a clone kept longer falls back to the ledger's normal rules. */
        private const val LEASE_TTL = "24h"

        /** machine-ledger's exit code for a claim that would leave less free space than its reserve. */
        private const val REFUSED = 3

        /** machine-ledger's exit code for a claim beyond its memory budget, or during a quiet measurement. */
        private const val REFUSED_MEMORY = 4
        private val TIMEOUT: Duration = Duration.ofMinutes(1)

        private fun leaseId(clone: String) = "tart:$clone"

        fun of(context: RunContext) = MachineLedger(
            locateMachineLedger(System.getenv("PATH"), Path.of(System.getProperty("user.home"))),
            context::log,
        ) { command -> context.subprocess.run(command, timeout = TIMEOUT) }
    }
}

/**
 * The `machine-ledger` executable on [searchPath], or in `~/.local/bin`, where it is installed but which a process
 * started outside a login shell may not have on its `PATH`; null when there is none.
 */
internal fun locateMachineLedger(
    searchPath: String?,
    home: Path
): Path? {
    val directories = searchPath.orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.map { Path.of(it) }
    // A Path is an Iterable of its names, so it is added as a list of one.
    return (directories + listOf(home.resolve(".local/bin")))
        .map { it.resolve("machine-ledger") }
        .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
}
