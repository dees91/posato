package app.posato.control.apple

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.model.StepError
import kotlinx.serialization.SerializationException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.readText

class DriverRun(
    val result: RunResult,
    val directory: Path,
) {
    fun snapshot(name: String): SnapshotNode? {
        val file = directory.resolve("$name.json")
        if (!file.exists()) return null
        return ControlJson.lenient.decodeFromString(SnapshotNode.serializer(), file.readText())
    }
}

class IosDriverRunner(
    private val context: RunContext,
    private val xcodeBuild: XcodeBuild,
    private val target: Target,
) {
    /** The driver's `.xctestrun`, rebuilt whenever the driver sources are newer than the last build. */
    fun ensureBuilt(udid: String?): Path {
        val existing = xcodeBuild.driverTestRun(target)
        if (existing != null && xcodeBuild.driverState(target) == XcodeBuild.DriverState.FRESH) return existing
        context.log(if (existing == null) "Building the iOS driver" else "Rebuilding the iOS driver because its sources changed")
        return xcodeBuild.buildDriver(target, udid)
    }

    fun run(
        scenario: Scenario,
        udid: String,
        bundleId: String
    ): DriverRun {
        val testRun = ensureBuilt(udid)
        val invocation = nextInvocation()
        val resultBundle = context.artifactPath("driver", "$target-$invocation.xcresult")
        val encoded = Base64.getEncoder().encodeToString(ControlJson.compact.encodeToString(Scenario.serializer(), scenario).toByteArray())
        val log = context.recordArtifact(context.artifactPath("driver", "$target-$invocation.xcodebuild.log"))
        val secrets = DriverSecrets.environment(scenario, DriverSecrets.keychainReader(context))
        val exitCode = xcodeBuild.testWithoutBuilding(
            testRun,
            udid,
            resultBundle,
            mapOf("POSATO_SCENARIO_B64" to encoded, "POSATO_BUNDLE_ID" to bundleId) + secrets,
            log,
            redact = if (secrets.isEmpty()) { text -> text } else DriverSecrets::redactKeyTaps,
        )
        if (log.exists()) driverLogFailure(log.readText())?.let { throw it }
        if (!resultBundle.exists()) {
            throw ControlException(
                ErrorCode.DRIVER_FAILED,
                "The driver produced no result bundle (xcodebuild exit $exitCode).",
                "Unlock the device, keep it connected, and read ${context.layout.relativize(log)}.",
            )
        }
        val exported = XcresultExport(context).exportAttachments(resultBundle, context.artifactPath("driver", "$target-$invocation"))
        return DriverRun(relocate(readResult(exported, exitCode), exported), exported)
    }

    private fun readResult(
        exported: Path,
        exitCode: Int
    ): RunResult {
        val resultFile = exported.resolve("result.json")
        if (!resultFile.exists()) {
            throw ControlException(
                ErrorCode.DRIVER_FAILED,
                "The driver finished (xcodebuild exit $exitCode) without a result.json attachment.",
                "Read the xcodebuild log next to the result bundle in the run directory.",
            )
        }
        return try {
            ControlJson.lenient.decodeFromString(RunResult.serializer(), resultFile.readText())
        } catch (exception: SerializationException) {
            throw ControlException(ErrorCode.DRIVER_FAILED, "Unreadable driver result: ${exception.message}", cause = exception)
        }
    }

    private fun nextInvocation(): Int {
        val directory = context.artifactPath("driver", "placeholder").parent
        val existing = directory.toFile().listFiles { file -> file.name.startsWith("$target-") && file.name.endsWith(".xcresult") }.orEmpty()
        return existing.mapNotNull { file -> file.name.removePrefix("$target-").removeSuffix(".xcresult").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
    }

    private fun relocate(
        result: RunResult,
        directory: Path
    ): RunResult {
        val steps = result.steps.map { step ->
            val artifacts = step.artifacts.map { name -> context.layout.relativize(context.recordArtifact(directory.resolve(name))) }
            step.copy(artifacts = artifacts)
        }
        return result.copy(steps = steps)
    }

    companion object {
        fun failure(message: String): RunResult = RunResult(false, emptyList(), StepError(ErrorCode.DRIVER_FAILED.name, message))
    }
}

private const val AUTOMATION_MODE_TIMEOUT = "Timed out while enabling automation mode"

/**
 * Recognizes a driver run that iOS refused before any step ran. After a restart, or the first time UI
 * automation is used, the device waits for its owner to unlock it and enter the passcode for XCTest.
 */
internal fun driverLogFailure(log: String): ControlException? = if (log.contains(AUTOMATION_MODE_TIMEOUT)) {
    ControlException(
        ErrorCode.DEVICE_AUTOMATION_LOCKED,
        "iOS did not enable UI automation for the driver.",
        "Ask the device owner to unlock the iPhone and, if it asks, enter the passcode for XCTest; then run the command again.",
    )
} else {
    null
}
