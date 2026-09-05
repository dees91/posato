package app.posato.provisioning

import app.posato.provisioning.cli.DevicesCommand
import app.posato.provisioning.cli.DevicesRegisterCommand
import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningJson
import app.posato.provisioning.model.Envelope
import app.posato.provisioning.model.ErrorPayload
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.ParameterFormatter
import kotlin.system.exitProcess

private const val EXIT_USAGE = 2

class PosatoProvisioning : CliktCommand(name = "posato-provisioning") {
    override fun help(context: Context): String =
        "Provisions Apple development resources for the Posato App IDs through the App Store Connect API: report " +
            "readiness, register this Mac and the connected iPhone, ensure a development certificate, and install " +
            "development profiles. Every command prints a JSON envelope and never prints a credential or identifier."

    override fun run() = Unit
}

fun buildCommand(): PosatoProvisioning = PosatoProvisioning().subcommands(
    DevicesCommand().subcommands(DevicesRegisterCommand()),
)

fun run(args: Array<String>): Int {
    val command = buildCommand()
    return try {
        command.parse(args.toList())
        0
    } catch (result: ProgramResult) {
        result.statusCode
    } catch (error: UsageError) {
        emitUsageError(error)
        command.echoFormattedHelp(error)
        EXIT_USAGE
    } catch (error: CliktError) {
        command.echoFormattedHelp(error)
        error.statusCode
    }
}

private fun emitUsageError(error: UsageError) {
    val message = error.context?.let { context -> error.formatMessage(context.localization, ParameterFormatter.Plain) }
        ?: error.message
        ?: "Invalid usage."
    val envelope = Envelope(
        ok = false,
        command = "posato-provisioning",
        durationMs = 0,
        error = ErrorPayload(ErrorCode.USAGE.name, message, "Run `posato-provisioning --help` or `posato-provisioning <command> --help`."),
    )
    println(ProvisioningJson.pretty.encodeToString(Envelope.serializer(), envelope))
}

fun main(args: Array<String>) {
    exitProcess(run(args))
}
