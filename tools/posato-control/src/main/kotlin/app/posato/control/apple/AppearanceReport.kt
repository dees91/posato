package app.posato.control.apple

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.RunResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

/** The system appearance an iOS driver run's `appearance` step reported: before the step and after it. */
data class AppearanceReport(
    val before: String,
    val after: String,
) {
    companion object {
        /** Reads the report the step at [index] attached; [root] resolves the artifact paths the run lists. */
        fun read(
            root: Path,
            result: RunResult,
            index: Int = 0,
        ): AppearanceReport {
            val artifact = result.steps.getOrNull(index)?.artifacts?.firstOrNull()
                ?: throw ControlException(ErrorCode.DRIVER_FAILED, "The driver attached no appearance report.")
            val report = parse(root, artifact)
            val before = report["before"]?.jsonPrimitive?.content
            val after = report["after"]?.jsonPrimitive?.content
            if (before == null || after == null) throw ControlException(ErrorCode.DRIVER_FAILED, "The appearance report $artifact names no mode.")
            return AppearanceReport(before, after)
        }

        private fun parse(
            root: Path,
            artifact: String,
        ): JsonObject = try {
            ControlJson.lenient.parseToJsonElement(root.resolve(artifact).readText()).jsonObject
        } catch (exception: IOException) {
            throw unreadable(artifact, exception)
        } catch (exception: IllegalArgumentException) {
            // Also a SerializationException, which extends it.
            throw unreadable(artifact, exception)
        }

        private fun unreadable(
            artifact: String,
            cause: Exception,
        ) = ControlException(ErrorCode.DRIVER_FAILED, "The appearance report $artifact is unreadable: ${cause.message}", cause = cause)
    }
}
