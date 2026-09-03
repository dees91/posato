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
    private var invocation = 0

    fun ensureBuilt(udid: String?): Path = xcodeBuild.driverTestRun(target) ?: xcodeBuild.buildDriver(target, udid)

    fun run(
        scenario: Scenario,
        udid: String,
        bundleId: String
    ): DriverRun {
        val testRun = ensureBuilt(udid)
        invocation += 1
        val resultBundle = context.artifactPath("driver", "$target-$invocation.xcresult")
        Files.deleteIfExists(resultBundle)
        val encoded = Base64.getEncoder().encodeToString(ControlJson.compact.encodeToString(Scenario.serializer(), scenario).toByteArray())
        val log = context.recordArtifact(context.artifactPath("driver", "$target-$invocation.xcodebuild.log"))
        val exitCode = xcodeBuild.testWithoutBuilding(
            testRun,
            udid,
            resultBundle,
            mapOf("POSATO_SCENARIO_B64" to encoded, "POSATO_BUNDLE_ID" to bundleId),
            log,
        )
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
