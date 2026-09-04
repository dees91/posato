package app.posato.control.cli

import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.model.Actions
import app.posato.control.model.LaunchConfiguration
import app.posato.control.model.Query
import app.posato.control.model.RunResult
import app.posato.control.model.Scenario
import app.posato.control.model.SnapshotNode
import app.posato.control.model.States
import app.posato.control.model.Step
import app.posato.control.scenario.QueryMatcher
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

private fun singleStep(step: Step): Scenario = Scenario(launch = LaunchConfiguration(terminateExisting = false), steps = listOf(step))

private fun requireQuery(
    query: Query?,
    action: String
): Query = query
    ?: throw ControlException(ErrorCode.USAGE, "$action needs an element query.", "Pass --text, --id, --role, --path, or --text-contains.")

private fun runResultElement(result: RunResult): JsonElement = ControlJson.pretty.encodeToJsonElement(RunResult.serializer(), result)

private fun failIfStepFailed(result: RunResult): RunResult {
    val failed = result.steps.firstOrNull { !it.ok }?.error ?: result.error
    if (failed != null) {
        val code = ErrorCode.entries.firstOrNull { it.name == failed.code } ?: ErrorCode.COMMAND_FAILED
        throw ControlException(code, failed.message)
    }
    return result
}

class SnapshotCommand : ControlCommand("snapshot", "Dump the accessibility tree as JSON (or an indented outline with --format text).") {
    private val query by QueryOptions()
    private val process by ProcessOptions()
    private val maxDepth by option("--max-depth", help = "Limit the tree depth.").int()
    private val format by option("--format", help = "json or text").default("json")

    override fun execute(session: Session): JsonElement {
        val node = session.backend(process.selector()).snapshot(query.toQuery(), maxDepth)
        return if (format == "text") JsonPrimitive(node.outline()) else ControlJson.pretty.encodeToJsonElement(SnapshotNode.serializer(), node)
    }
}

class FindCommand : ControlCommand("find", "Return every element matching the query without touching the application.") {
    private val query by QueryOptions()
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement {
        val root = session.backend(process.selector()).snapshot(null, null)
        val matches = QueryMatcher.findAll(root, requireQuery(query.toQuery(), "find")).map { it.copy(children = emptyList()) }
        return ControlJson.pretty.encodeToJsonElement(ListSerializer(SnapshotNode.serializer()), matches)
    }
}

class TapCommand : ControlCommand("tap", "Press a button or tap an element matching the query.") {
    private val query by QueryOptions()
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement = runResultElement(
        failIfStepFailed(
            session.backend(process.selector())
                .runScenario(singleStep(Step(action = Actions.TAP, query = requireQuery(query.toQuery(), "tap")))),
        ),
    )
}

class TypeCommand : ControlCommand("type", "Type text into the element matching the query.") {
    private val query by QueryOptions()
    private val text by option("--text-input", "--input", help = "Text to type.").required()
    private val clear by option("--clear", help = "Clear the field first.").flag()
    private val submit by option("--submit", help = "Press return after typing.").flag()
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement {
        val backend = session.backend(process.selector())
        val target = query.toQuery()
        // Addressing a process without a query types into whatever it has focused, the only path into a panel with no tree.
        if (target == null && process.selector() != null) {
            backend.typeFocused(text, clear, submit)
            return buildJsonObject { put("ok", true) }
        }
        val step = Step(action = Actions.TYPE, query = requireQuery(target, "type"), text = text, clear = clear, submit = submit)
        return runResultElement(failIfStepFailed(backend.runScenario(singleStep(step))))
    }
}

class PressCommand :
    ControlCommand("press", "Press a key: return, escape, tab, delete, space, arrows, letters (desktop with --modifiers), or home (iOS).") {
    private val key by option("--key", help = "Key name.").required()
    private val modifiers by option("--modifiers", help = "Comma-separated: cmd, shift, alt, ctrl (desktop only).").split(",").default(emptyList())
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement = runResultElement(
        failIfStepFailed(
            session.backend(process.selector())
                .runScenario(singleStep(Step(action = Actions.PRESS, key = key, modifiers = modifiers))),
        ),
    )
}

class WaitCommand : ControlCommand("wait", "Wait until an element exists, is absent, enabled, disabled, or until the UI has settled.") {
    private val query by QueryOptions()
    private val state by option("--for", help = "exists, absent, enabled, disabled, or settled.").required()
    private val timeout by option("--timeout-seconds", help = "Give up after this many seconds.").double().default(DEFAULT_WAIT_SECONDS)
    private val process by ProcessOptions()

    override fun execute(session: Session): JsonElement {
        val backend = session.backend(process.selector())
        val target = query.toQuery()
        // Addressing a process without a query waits for its window, which needs no element tree.
        if (target == null && process.selector() != null && state == States.EXISTS) {
            backend.awaitWindow(timeout)
            return buildJsonObject { put("ok", true) }
        }
        val step = Step(action = Actions.WAIT_FOR, state = state, query = target, timeoutSeconds = timeout)
        return runResultElement(failIfStepFailed(backend.runScenario(singleStep(step))))
    }

    private companion object {
        const val DEFAULT_WAIT_SECONDS = 10.0
    }
}

class RunCommand : ControlCommand("run", "Run a JSON scenario file (or - for stdin) and report every step with its evidence.") {
    private val scenario by option("--scenario", help = "Scenario file path, or - to read standard input.").required()

    override fun execute(session: Session): JsonElement {
        val text = if (scenario == "-") generateSequence(::readlnOrNull).joinToString("\n") else Files.readString(Path.of(scenario))
        val result = session.backend().runScenario(parseScenario(text))
        if (!result.ok) {
            session.context.log(ControlJson.pretty.encodeToString(RunResult.serializer(), result))
            val error = result.steps.firstOrNull { !it.ok }?.error ?: result.error
            val code = ErrorCode.entries.firstOrNull { it.name == error?.code } ?: ErrorCode.COMMAND_FAILED
            throw ControlException(
                code,
                error?.message ?: "The scenario failed.",
                "Inspect the step results and failure artifacts in the run directory.",
            )
        }
        return runResultElement(result)
    }
}

private fun parseScenario(text: String): Scenario {
    val parsed = try {
        ControlJson.lenient.decodeFromString(Scenario.serializer(), text)
    } catch (exception: SerializationException) {
        throw ControlException(ErrorCode.SCENARIO_INVALID, "The scenario is not valid: ${exception.message}", cause = exception)
    }
    parsed.steps.firstOrNull { it.action !in Actions.all }?.let { step ->
        throw ControlException(ErrorCode.SCENARIO_INVALID, "Unknown action '${step.action}'.", "Valid actions: ${Actions.all.joinToString(", ")}.")
    }
    return parsed
}
