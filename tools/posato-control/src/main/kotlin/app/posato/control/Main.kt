package app.posato.control

import app.posato.control.cli.ArtifactsCommand
import app.posato.control.cli.BuildCommand
import app.posato.control.cli.CleanupCommand
import app.posato.control.cli.DbCommand
import app.posato.control.cli.DbPathCommand
import app.posato.control.cli.DbQueryCommand
import app.posato.control.cli.DevicesBootCommand
import app.posato.control.cli.DevicesCommand
import app.posato.control.cli.DevicesListCommand
import app.posato.control.cli.DevicesShutdownCommand
import app.posato.control.cli.DoctorCommand
import app.posato.control.cli.FindCommand
import app.posato.control.cli.InstallCommand
import app.posato.control.cli.LaunchCommand
import app.posato.control.cli.LogsCommand
import app.posato.control.cli.ObserveCommand
import app.posato.control.cli.OrientCommand
import app.posato.control.cli.PressCommand
import app.posato.control.cli.ResetCommand
import app.posato.control.cli.RunCommand
import app.posato.control.cli.ScreenshotCommand
import app.posato.control.cli.SnapshotCommand
import app.posato.control.cli.StatusCommand
import app.posato.control.cli.TapCommand
import app.posato.control.cli.TerminateCommand
import app.posato.control.cli.TypeCommand
import app.posato.control.cli.VmBootCommand
import app.posato.control.cli.VmClickCommand
import app.posato.control.cli.VmCommand
import app.posato.control.cli.VmCreateCommand
import app.posato.control.cli.VmDestroyCommand
import app.posato.control.cli.VmDragCommand
import app.posato.control.cli.VmICloudCommand
import app.posato.control.cli.VmInstallCommand
import app.posato.control.cli.VmPressCommand
import app.posato.control.cli.VmPromptCommand
import app.posato.control.cli.VmScreenshotCommand
import app.posato.control.cli.VmShutdownCommand
import app.posato.control.cli.VmSyncCommand
import app.posato.control.cli.VmTypeCommand
import app.posato.control.cli.WaitCommand
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Envelope
import app.posato.control.model.ErrorPayload
import app.posato.control.vm.GuestRelay
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

class PosatoControl : CliktCommand(name = "posato-control") {
    override fun help(context: Context): String =
        "Agent-facing driver for the Posato macOS and iOS applications: build, launch, drive, inspect, screenshot, and reset. " +
            "Every command prints a JSON envelope and accepts --target/-t desktop|simulator|device."

    override fun run() = Unit
}

fun buildCommand(): PosatoControl = PosatoControl().subcommands(
    DoctorCommand(),
    DevicesCommand().subcommands(DevicesListCommand(), DevicesBootCommand(), DevicesShutdownCommand()),
    BuildCommand(),
    InstallCommand(),
    LaunchCommand(),
    TerminateCommand(),
    StatusCommand(),
    ScreenshotCommand(),
    SnapshotCommand(),
    FindCommand(),
    TapCommand(),
    TypeCommand(),
    PressCommand(),
    OrientCommand(),
    WaitCommand(),
    RunCommand(),
    LogsCommand(),
    DbCommand().subcommands(DbPathCommand(), DbQueryCommand()),
    ResetCommand(),
    CleanupCommand(),
    ArtifactsCommand(),
    ObserveCommand(),
    VmCommand().subcommands(
        VmCreateCommand(),
        VmSyncCommand(),
        VmBootCommand(),
        VmShutdownCommand(),
        VmTypeCommand(),
        VmInstallCommand(),
        VmDestroyCommand(),
        VmPromptCommand(),
        VmClickCommand(),
        VmDragCommand(),
        VmPressCommand(),
        VmScreenshotCommand(),
        VmICloudCommand(),
    ),
)

fun run(args: Array<String>): Int {
    GuestRelay.lineIn(args.toList())?.let { line -> return GuestRelay.forward(args.toList(), line) }
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
        command = "posato-control",
        runId = "none",
        durationMs = 0,
        error = ErrorPayload(ErrorCode.USAGE.name, message, "Run `posato-control --help` or `posato-control <command> --help`."),
    )
    println(ControlJson.pretty.encodeToString(Envelope.serializer(), envelope))
}

fun main(args: Array<String>) {
    exitProcess(run(args))
}
