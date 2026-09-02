package app.posato.control.apple

import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class XcresultExport(
    private val context: RunContext
) {
    fun exportAttachments(
        resultBundle: Path,
        destination: Path
    ): Path {
        Files.createDirectories(destination)
        val exportDirectory = destination.resolve("raw")
        context.subprocess.run(
            listOf(
                Simctl.XCRUN,
                "xcresulttool",
                "export",
                "attachments",
                "--path",
                resultBundle.toString(),
                "--output-path",
                exportDirectory.toString(),
            ),
        )
            .requireSuccess(ErrorCode.DRIVER_FAILED, "Exporting driver attachments")
        val manifest = exportDirectory.resolve("manifest.json")
        if (!manifest.exists()) return destination
        attachments(manifest).forEach { (exportedName, humanName) ->
            val source = exportDirectory.resolve(exportedName)
            if (source.exists()) Files.copy(source, destination.resolve(humanName), StandardCopyOption.REPLACE_EXISTING)
        }
        return destination
    }

    private fun attachments(manifest: Path): List<Pair<String, String>> = try {
        val root = ControlJson.lenient.parseToJsonElement(manifest.readText())
        collect(root)
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    private fun collect(element: JsonElement): List<Pair<String, String>> = when {
        element is kotlinx.serialization.json.JsonObject -> {
            val exported = element["exportedFileName"]?.jsonPrimitive?.content
            val human = element["suggestedHumanReadableName"]?.jsonPrimitive?.content
            val own = if (exported != null && human != null) listOf(exported to human) else emptyList()
            own + element.values.flatMap { collect(it) }
        }

        element is kotlinx.serialization.json.JsonArray -> {
            element.jsonArray.flatMap { collect(it) }
        }

        else -> {
            emptyList()
        }
    }
}
