package app.posato.provisioning.cli

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.Envelope
import app.posato.provisioning.model.ErrorPayload
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The shared command shape: time the run, turn any failure into one categorical envelope, and redact it.
 *
 * Redaction is applied here rather than at each throw site so a message from a helper, a parser, or the runtime
 * cannot leak a configured value by being written before anyone remembered to redact it.
 */
abstract class ProvisioningCommand(
    name: String,
    private val helpText: String,
) : CliktCommand(name = name) {
    private val group by GlobalOptionsGroup()

    private var reported: ProvisioningException? = null

    /**
     * Records a truthful failure that is not an exception.
     *
     * `doctor` has to print its whole report and still exit non-zero when a condition is missing, so the envelope
     * carries the result and the error together rather than one replacing the other.
     */
    protected fun reportFailure(
        code: ErrorCode,
        message: String,
        hint: String
    ) {
        reported = ProvisioningException(code, message, hint)
    }

    override fun help(context: Context): String = helpText

    protected abstract fun execute(session: Session): JsonElement?

    override fun run() {
        val started = System.nanoTime()
        val globals = group.toGlobalOptions()
        var session: Session? = null
        var failure: ProvisioningException? = null
        val result = try {
            session = Session(globals)
            execute(session)
        } catch (exception: ProvisioningException) {
            failure = exception
            null
        } catch (exception: IOException) {
            failure = wrap(exception)
            null
        } catch (exception: SerializationException) {
            // Never the exception's own message: a kotlinx decoding failure quotes the input around the offset, so
            // a malformed App Store Connect response would print a slice of a document holding other devices'
            // identifiers, which nothing has registered as a secret.
            failure = ProvisioningException(
                ErrorCode.COMMAND_FAILED,
                "A document could not be read.",
                "Rerun with --verbose for the redacted request transcript.",
                exception,
            )
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
        failure = failure ?: reported
        val redact: (String) -> String = { text -> session?.redaction?.redact(text) ?: text }
        val envelope = Envelope(
            ok = failure == null,
            command = commandName,
            durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI,
            result = result,
            error = failure?.let { ErrorPayload(it.code.name, redact(it.message ?: it.code.name), it.hint?.let(redact)) },
        )
        emit(envelope, globals.human)
        throw ProgramResult(failure?.code?.exitCode ?: 0)
    }

    private fun wrap(exception: Exception): ProvisioningException = ProvisioningException(
        ErrorCode.COMMAND_FAILED,
        "${exception::class.simpleName}: ${exception.message}",
        "Rerun with --verbose for the redacted helper and request transcript.",
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
                echo(text ?: ProvisioningJson.pretty.encodeToString(JsonElement.serializer(), result))
            }
        } else {
            echo(ProvisioningJson.pretty.encodeToString(Envelope.serializer(), envelope))
        }
    }
}
