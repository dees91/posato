package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

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
        val durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI
        val envelope = Envelope(
            ok = failure == null,
            command = commandName,
            target = globals.target?.id,
            runId = session?.context?.runId ?: globals.runId ?: "none",
            durationMs = durationMs,
            result = result,
            artifacts = session?.context?.artifacts ?: emptyList(),
            error = failure?.let { ErrorPayload(it.code.name, it.message ?: it.code.name, it.hint) },
        )
        session?.context?.updateLatestLink()
        emit(envelope, globals.human)
        throw ProgramResult(failure?.code?.exitCode ?: 0)
    }

    private fun wrap(exception: Exception): ControlException = ControlException(
        ErrorCode.COMMAND_FAILED,
        "${exception::class.simpleName}: ${exception.message}",
        "Rerun with --verbose for the helper transcript.",
        exception,
    )

    private fun emit(
        envelope: Envelope,
        human: Boolean
    ) {
        if (human) {
            echo(if (envelope.ok) "ok (${envelope.durationMs} ms)" else "error ${envelope.error?.code}: ${envelope.error?.message}")
            envelope.error?.hint?.let { echo("hint: $it") }
            envelope.result?.let { result ->
                val text = (result as? JsonPrimitive)?.takeIf { it.isString }?.content
                echo(text ?: ControlJson.pretty.encodeToString(JsonElement.serializer(), result))
            }
            envelope.artifacts.forEach { echo("artifact: $it") }
        } else {
            echo(ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
