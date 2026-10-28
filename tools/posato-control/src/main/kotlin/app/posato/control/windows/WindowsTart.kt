package app.posato.control.windows

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.ProcessOutput
import app.posato.control.core.RunContext
import app.posato.control.vm.TartVm
import app.posato.control.vm.parseTartList
import app.posato.control.vm.parseTartMemory
import java.nio.file.Path
import java.time.Duration

/**
 * The Tart build with Windows guests. Upstream Tart runs only macOS and Linux guests, so Windows uses a separately
 * built fork ([ConfigurationKey.WINDOWS_TART]) with its own `TART_HOME` ([ConfigurationKey.WINDOWS_TART_HOME], by
 * default `~/.tart-windows`), which keeps its VMs apart from the macOS and Linux ones. Commands reach the guest through
 * the fork's Windows guest agent, which runs in the signed-in console session.
 */
internal class WindowsTart(
    private val context: RunContext,
) {
    val binary: String = context.configuration.value(ConfigurationKey.WINDOWS_TART)
        ?: throw ControlException(
            ErrorCode.PRECONDITION_NOT_MET,
            "The Windows target needs the Tart build with Windows guests.",
            "Set ${ConfigurationKey.WINDOWS_TART.propertyName} in local.properties or export " +
                "${ConfigurationKey.WINDOWS_TART.environmentName}; docs/development/unattended-verification.md names the build.",
        )

    val home: Path = context.configuration.value(ConfigurationKey.WINDOWS_TART_HOME)?.let { Path.of(expandHome(it)) }
        ?: Path.of(System.getProperty("user.home"), DEFAULT_HOME)

    private val environment: Map<String, String> = mapOf("TART_HOME" to home.toString())

    fun list(): List<TartVm> = parseTartList(run(listOf(binary, "list", "--format", "json"), "Listing the Windows Tart VMs").stdout)

    fun memoryMib(name: String): Int? = context.subprocess
        .run(listOf(binary, "get", name, "--format", "json"), environment = environment, timeout = SHORT)
        .takeIf { it.exitCode == 0 }
        ?.let { parseTartMemory(it.stdout) }

    fun clone(
        source: String,
        name: String,
    ) {
        run(listOf(binary, "clone", source, name), "Cloning $source", CLONE)
    }

    fun delete(name: String) {
        run(listOf(binary, "delete", name), "Deleting $name")
    }

    fun stop(name: String) {
        context.subprocess.run(listOf(binary, "stop", name, "--timeout", STOP_SECONDS.toString()), environment = environment, timeout = STOP)
    }

    /** Boots [name] headless with each of [shares] (`name:path`) as a directory share; the guest maps them on `Z:`. */
    fun start(
        name: String,
        shares: List<String>,
        log: Path,
    ): Process = context.subprocess.startDetached(
        listOf(binary, "run", name, "--no-graphics") + shares.map { "--dir=$it" },
        log,
        environment = environment,
    )

    /** Runs [command] in the guest's signed-in session, with [stdin] as its standard input when given. */
    fun exec(
        name: String,
        command: List<String>,
        stdin: ByteArray? = null,
        timeout: Duration = EXEC,
    ): ProcessOutput {
        val arguments = buildList {
            addAll(listOf(binary, "exec"))
            if (stdin != null) add("-i")
            add(name)
            addAll(command)
        }
        return context.subprocess.run(arguments, environment = environment, timeout = timeout, stdinBytes = stdin)
    }

    private fun run(
        command: List<String>,
        what: String,
        timeout: Duration = EXEC,
    ): ProcessOutput = context.subprocess.run(command, environment = environment, timeout = timeout).requireSuccess(ErrorCode.COMMAND_FAILED, what)

    companion object {
        const val DEFAULT_HOME = ".tart-windows"
        private const val STOP_SECONDS = 60
        private val SHORT: Duration = Duration.ofMinutes(1)
        private val EXEC: Duration = Duration.ofMinutes(10)
        private val CLONE: Duration = Duration.ofMinutes(5)
        private val STOP: Duration = Duration.ofMinutes(2)

        private fun expandHome(path: String): String =
            if (path == "~" || path.startsWith("~/")) System.getProperty("user.home") + path.removePrefix("~") else path
    }
}
