package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.linux.LinuxGuest
import app.posato.control.relay.FolderRelay
import app.posato.control.relay.RelayEndpoint
import app.posato.control.relay.ShellEndpoint
import app.posato.control.vm.Tart
import app.posato.control.vm.VmLine
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * Stands in for Dropbox, OneDrive, or Syncthing during folder workspace runs (ADR 0010): each listed device keeps its
 * own local folder, and the relay copies changes and deletions between them through the host folder
 * `build/verification/sync-folder` until `--duration-seconds` has passed.
 */
class RelayCommand : ControlCommand("relay", "Synchronize the devices' local Posato folders through a host folder, like a folder sync service.") {
    private val lines by option("--line", help = "A Tart line whose guest folder joins; repeat for more.").multiple()
    private val androidSerials by option("--android", help = "An adb serial whose folder joins; repeat for more.").multiple()
    private val linux by option("--linux", help = "The Linux clone's folder joins.").flag()
    private val intervalSeconds by option("--interval-seconds", help = "Pause between rounds.").long().default(DEFAULT_INTERVAL_SECONDS)
    private val durationSeconds by option("--duration-seconds", help = "How long to keep relaying.").long().default(DEFAULT_DURATION_SECONDS)

    override fun execute(session: Session): JsonElement {
        val tart = Tart(session.context)
        val endpoints: List<RelayEndpoint> = lines.map { id ->
            val line = VmLine.parse(id)
            ShellEndpoint("vm:$id", GUEST_FOLDER, { script, stdin ->
                tart.exec(line.cloneName, script, stdin).requireSuccess(ErrorCode.COMMAND_FAILED, "Relaying ${line.cloneName}").stdout
            })
        } + listOfNotNull(
            if (linux) {
                // The Linux clone's folder is a host directory shared into it, so no command enters the guest.
                ShellEndpoint("linux", "'" + LinuxGuest.syncShare(session.context) + "'", { script, stdin ->
                    session.context.subprocess.run(listOf("/bin/sh", "-c", script), stdin = stdin)
                        .requireSuccess(ErrorCode.COMMAND_FAILED, "Relaying the Linux folder").stdout
                })
            } else {
                null
            },
        ) + androidSerials.map { serial ->
            ShellEndpoint("android:$serial", ANDROID_FOLDER, { script, stdin ->
                session.context.subprocess.run(listOf("adb", "-s", serial, "shell", script), stdin = stdin)
                    .requireSuccess(ErrorCode.COMMAND_FAILED, "Relaying $serial").stdout
            })
        }
        val relay = FolderRelay(session.layout.syncFolder, endpoints)
        val deadline = System.currentTimeMillis() + durationSeconds * MILLIS_PER_SECOND
        var rounds = 0
        // Two relays over the same hub would race on the same files, so a second one refuses to start.
        val lockFile = session.layout.syncFolder.resolveSibling("relay.lock")
        Files.createDirectories(lockFile.parent)
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.tryLock() ?: throw ControlException(ErrorCode.ALREADY_RUNNING, "Another relay is running", "Stop it before starting a new one.")
            while (System.currentTimeMillis() < deadline) {
                relay.round()
                rounds++
                Thread.sleep(intervalSeconds * MILLIS_PER_SECOND)
            }
        }
        return buildJsonObject {
            put("rounds", rounds)
            put("copies", relay.copies)
            put("deletions", relay.deletions)
        }
    }

    companion object {
        /** Each guest's folder: `$HOME/PosatoSync`, on its own disk, so no guest reads another's file system cache. */
        const val GUEST_FOLDER = "\"\$HOME/PosatoSync\""
        const val ANDROID_FOLDER = "/sdcard/PosatoSync"
        private const val DEFAULT_INTERVAL_SECONDS = 3L
        private const val DEFAULT_DURATION_SECONDS = 1_800L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
