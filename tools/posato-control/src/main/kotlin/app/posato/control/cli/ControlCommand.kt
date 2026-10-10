package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import app.posato.control.model.humanLines
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

abstract class ControlCommand(
    name: String,
    private val helpText: String,
) : CliktCommand(name = name) {
    private val group by GlobalOptionsGroup()

    /** Whether the desktop target may run on the host Mac; only building and read-only checks may. */
    protected open val hostDesktopAllowed: Boolean = false

    override fun help(context: Context): String = helpText

    protected abstract fun execute(session: Session): JsonElement?

    override fun run() {
        val started = System.nanoTime()
        val globals = group.toGlobalOptions()
        var session: Session? = null
        var failure: ControlException? = null
        val result = try {
            session = Session(globals, hostDesktopAllowed)
            execute(session)
        } catch (exception: ControlException) {
            failure = exception
            null
        } catch (exception: IOException) {
            failure = wrap(exception)
            null
        } catch (exception: SerializationException) {
            failure = wrap(exception)
            null
        } catch (exception: IllegalStateException) {
            failure = wrap(exception)
            null
        } catch (exception: IllegalArgumentException) {
            failure = wrap(exception)
            null
        } catch (exception: NoSuchElementException) {
            failure = wrap(exception)
            null
        }
        report(globals, session, result, failure, started)
        throw ProgramResult(failure?.code?.exitCode ?: 0)
    }

    /** Prints the envelope, keeps a copy in the run directory, and points `latest` at the run. */
    private fun report(
        globals: GlobalOptions,
        session: Session?,
        result: JsonElement?,
        failure: ControlException?,
        started: Long,
    ) {
        val envelope = Envelope(
            ok = failure == null,
            command = commandName,
            target = globals.target?.id,
            runId = session?.context?.runId ?: globals.runId ?: "none",
            durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI,
            result = result ?: failure?.result,
            artifacts = session?.context?.artifacts ?: emptyList(),
            error = failure?.let { ErrorPayload(it.code.name, it.message ?: it.code.name, it.hint) },
        )
        session?.context?.let { context ->
            keepEnvelope(context.runDirectory, envelope)
            context.updateLatestLink()
        }
        emit(envelope, globals.human)
    }

    private fun wrap(exception: Exception): ControlException = ControlException(
        ErrorCode.COMMAND_FAILED,
        "${exception::class.simpleName}: ${exception.message}",
        "Rerun with --verbose for the helper transcript.",
        exception,
    )

    /**
     * Keeps every command's envelope in its run directory, so a run id cited in a `Verified` line always has a
     * directory, also for a host command such as `vm create` that writes no other artifact or a refusal. Without it
     * `pr-evidence` could not tell an honest citation from a mistyped one. A disk too full to write it loses only this
     * copy, never the command's output.
     */
    private fun keepEnvelope(
        runDirectory: Path,
        envelope: Envelope,
    ) {
        try {
            Files.createDirectories(runDirectory)
            Files.writeString(runDirectory.resolve(ENVELOPE_FILE), ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
        } catch (_: IOException) {
            // The printed envelope stays the record of the command.
        }
    }

    private fun emit(
        envelope: Envelope,
        human: Boolean
    ) {
        if (human) {
            envelope.humanLines().forEach { echo(it) }
        } else {
            echo(ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val ENVELOPE_FILE = "envelope.json"
    }
}
