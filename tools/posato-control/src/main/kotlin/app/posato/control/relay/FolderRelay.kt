package app.posato.control.relay

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/** One device's synchronized folder, reached through a shell: a Tart guest through `tart exec`, an emulator through `adb shell`. */
interface RelayEndpoint {
    val name: String

    /** Relative path to SHA-256 of every file, without temporary files. */
    fun list(): Map<String, String>

    fun pull(paths: Collection<String>): Map<String, ByteArray>

    fun push(files: Map<String, ByteArray>)

    fun delete(paths: Collection<String>)
}

/** Runs a POSIX shell script on the device with optional standard input and returns its standard output. */
fun interface DeviceShell {
    fun run(
        script: String,
        stdin: String?,
    ): String
}

/**
 * A folder endpoint any POSIX shell can serve: macOS and Linux guests and Android's toybox all have `find`, `base64`, and
 * a SHA-256 tool. Files travel base64-encoded on one line each, and a push writes a temporary name first and renames it,
 * as a synchronization client does.
 */
class ShellEndpoint(
    override val name: String,
    private val root: String,
    private val shell: DeviceShell,
) : RelayEndpoint {
    override fun list(): Map<String, String> {
        val script = "mkdir -p $root && cd $root && find . -type f ! -name '$TEMPORARY*' | while IFS= read -r f; do " +
            "h=\$( (sha256sum \"\$f\" 2>/dev/null || shasum -a 256 \"\$f\") | cut -d ' ' -f 1); echo \"\$h \${f#./}\"; done"
        return shell.run(script, null).lineSequence()
            .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.split(" ", limit = 2)?.takeIf { it.size == 2 } }
            .associate { (hash, path) -> path to hash }
    }

    override fun pull(paths: Collection<String>): Map<String, ByteArray> {
        if (paths.isEmpty()) return emptyMap()
        val script = "cd $root && while IFS= read -r f; do printf '%s\\t' \"\$f\"; base64 < \"\$f\" | tr -d '\\n'; echo; done"
        return shell.run(script, paths.joinToString("\n", postfix = "\n")).lineSequence()
            .mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[1].isNotEmpty() } }
            .associate { (path, data) -> path to Base64.getDecoder().decode(data) }
    }

    override fun push(files: Map<String, ByteArray>) {
        if (files.isEmpty()) return
        val script = "mkdir -p $root && cd $root && while IFS=\"\$(printf '\\t')\" read -r p d; do " +
            "dir=\$(dirname \"\$p\"); mkdir -p \"\$dir\"; printf %s \"\$d\" | base64 -d > \"\$dir/$TEMPORARY-relay\" && " +
            "mv \"\$dir/$TEMPORARY-relay\" \"\$p\"; done"
        val stdin = files.entries.joinToString("\n", postfix = "\n") { (path, bytes) -> path + "\t" + Base64.getEncoder().encodeToString(bytes) }
        shell.run(script, stdin)
    }

    override fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val script = "cd $root && while IFS= read -r f; do rm -f \"\$f\"; done; find . -mindepth 1 -type d -empty -delete 2>/dev/null; true"
        shell.run(script, paths.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val TEMPORARY = ".tmp-"
    }
}

/**
 * A small synchronization service for verification: every endpoint's folder converges on one hub folder on the host.
 * A change or deletion seen on an endpoint since the last round goes to the hub first, then the hub goes to every
 * endpoint, like a cloud folder. Two endpoints that change the same path in one round keep the later endpoint's copy.
 */
class FolderRelay(
    private val hub: Path,
    private val endpoints: List<RelayEndpoint>,
) {
    private val lastSeen = mutableMapOf<String, Map<String, String>>()
    var copies = 0
        private set
    var deletions = 0
        private set

    fun round() {
        Files.createDirectories(hub)
        for (endpoint in endpoints) collect(endpoint)
        val state = hubState()
        for (endpoint in endpoints) distribute(endpoint, state)
    }

    private fun collect(endpoint: RelayEndpoint) {
        val current = endpoint.list()
        val previous = lastSeen[endpoint.name] ?: emptyMap()
        val hubNow = hubState()
        val changed = current.filter { (path, hash) -> previous[path] != hash && hubNow[path] != hash }.keys
        endpoint.pull(changed).forEach { (path, bytes) ->
            val target = hub.resolve(path)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
            copies++
        }
        val removed = previous.keys - current.keys
        removed.forEach { path ->
            if (Files.deleteIfExists(hub.resolve(path))) deletions++
        }
        pruneEmptyDirectories()
    }

    private fun distribute(
        endpoint: RelayEndpoint,
        state: Map<String, String>,
    ) {
        val current = endpoint.list()
        val missing = state.filter { (path, hash) -> current[path] != hash }.keys
        endpoint.push(missing.associateWith { Files.readAllBytes(hub.resolve(it)) })
        val extra = current.keys - state.keys
        endpoint.delete(extra)
        copies += missing.size
        deletions += extra.size
        lastSeen[endpoint.name] = state
    }

    private fun hubState(): Map<String, String> {
        if (!hub.exists()) return emptyMap()
        return Files.walk(hub).use { paths ->
            paths.filter { it.isRegularFile() && !it.fileName.toString().startsWith(ShellEndpoint.TEMPORARY) }
                .toList()
                .associate { file -> file.relativeTo(hub).toString() to sha256(Files.readAllBytes(file)) }
        }
    }

    private fun pruneEmptyDirectories() {
        Files.walk(hub).use { paths ->
            paths.sorted(Comparator.reverseOrder())
                .filter { it != hub && Files.isDirectory(it) && Files.list(it).use { entries -> entries.findAny().isEmpty } }
                .forEach(Files::delete)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
