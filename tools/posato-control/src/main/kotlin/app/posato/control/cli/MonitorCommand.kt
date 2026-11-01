package app.posato.control.cli

import app.posato.control.apple.DeviceSession
import app.posato.control.apple.Devicectl
import app.posato.control.apple.IOS_BUNDLE_ID
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.Target
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files

/**
 * Reads the Device Activity registrations the iPhone app saw at its last evaluation (`SCHEDULE-007`): every monitored
 * activity name and the deadline the break activity stands for, from the app's own data container. The activity count
 * proves the monitor stays within the platform's budget; the deadline proves a break's return is registered.
 */
class MonitorCommand : ControlCommand("monitor", "Read the iPhone's monitored Device Activity names and the registered break deadline.") {
    override fun execute(session: Session): JsonElement {
        if (session.target() != Target.DEVICE) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "Device Activity registrations exist only on the test iPhone.",
                "Use -t device.",
            )
        }
        val device = DeviceSession(session.context, Devicectl(session.context), session.options.udid)
        val folder = Files.createTempDirectory("posato-control-monitor")
        try {
            val copy = folder.resolve(READOUT_NAME)
            device.devicectl.copyAppData(device.udid(), IOS_BUNDLE_ID, toDevice = false, copy, READOUT_PATH)
            if (!Files.isRegularFile(copy)) {
                throw ControlException(
                    ErrorCode.COMMAND_FAILED,
                    "The app has not written its monitor readout yet.",
                    "Launch Posato with Screen Time allowed and wait for one evaluation.",
                )
            }
            return ControlJson.lenient.parseToJsonElement(Files.readString(copy))
        } finally {
            folder.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val READOUT_NAME = "monitor-v1.json"
        const val READOUT_PATH = "Library/Application Support/ScheduleMonitor/$READOUT_NAME"
    }
}
