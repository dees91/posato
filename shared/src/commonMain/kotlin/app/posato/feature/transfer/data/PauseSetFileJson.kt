package app.posato.feature.transfer.data

import app.posato.feature.schedules.domain.ScheduleLimits
import app.posato.feature.sync.domain.ScheduleWireRules
import app.posato.feature.sync.domain.SyncFormatLimits
import app.posato.feature.targets.domain.ExactDomain
import app.posato.feature.targets.domain.ExactDomainPolicyLimits
import app.posato.feature.targets.domain.normalizeApplicationPolicyNameNfc
import app.posato.feature.transfer.domain.FileSchedule
import app.posato.feature.transfer.domain.FileSet
import app.posato.feature.transfer.domain.PauseSetFile
import app.posato.feature.transfer.domain.PauseSetFileLimits
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Why a file's contents cannot be imported, each with its own message. */
internal enum class PauseSetFileProblem {
    /** Not a Posato export at all, or not readable as one. */
    NOT_A_POSATO_FILE,

    /** Made by a newer Posato. */
    NEWER_VERSION,

    /** A set, website, schedule, or reference that breaks the format's rules. */
    INVALID_CONTENTS,

    /** More sets, schedules, or websites than Posato keeps. */
    OVER_LIMIT,
}

internal sealed interface PauseSetJsonResult {
    data class Read(
        val file: PauseSetFile,
    ) : PauseSetJsonResult

    data class Refused(
        val problem: PauseSetFileProblem,
    ) : PauseSetJsonResult
}

/** The JSON of a plain export. Writing and reading are strict about types and lenient about unknown fields. */
internal object PauseSetFileJson {
    private val json = Json { prettyPrint = true }

    fun encode(file: PauseSetFile): ByteArray {
        val root = buildJsonObject {
            put("format", PauseSetFileLimits.FORMAT)
            put("version", PauseSetFileLimits.CONTENT_VERSION)
            put(
                "sets",
                buildJsonArray {
                    file.sets.forEach { set ->
                        add(
                            buildJsonObject {
                                put("ref", set.ref)
                                put("name", set.name?.let(::JsonPrimitive) ?: JsonNull)
                                put("first", set.first)
                                put("domains", JsonArray(set.domains.map { domain -> JsonPrimitive(domain.canonicalValue) }))
                            },
                        )
                    }
                },
            )
            put("defaultSet", file.defaultSet?.let(::JsonPrimitive) ?: JsonNull)
            put(
                "schedules",
                buildJsonArray {
                    file.schedules.forEach { schedule ->
                        add(
                            buildJsonObject {
                                put("name", schedule.name)
                                put("weekdays", schedule.weekdays)
                                put("start", schedule.startMinute)
                                put("end", schedule.endMinute)
                                put("set", schedule.set)
                            },
                        )
                    }
                },
            )
        }
        return json.encodeToString(JsonElement.serializer(), root).encodeToByteArray()
    }

    fun decode(bytes: ByteArray): PauseSetJsonResult {
        if (bytes.size > PauseSetFileLimits.MAX_FILE_BYTES || nestingDepth(bytes) > PauseSetFileLimits.MAX_JSON_DEPTH) {
            return PauseSetJsonResult.Refused(PauseSetFileProblem.NOT_A_POSATO_FILE)
        }
        val root = try {
            Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: CharacterCodingException) {
            null
        } ?: return PauseSetJsonResult.Refused(PauseSetFileProblem.NOT_A_POSATO_FILE)
        if (root.string("format") != PauseSetFileLimits.FORMAT) {
            return PauseSetJsonResult.Refused(PauseSetFileProblem.NOT_A_POSATO_FILE)
        }
        val version = root.int("version") ?: return PauseSetJsonResult.Refused(PauseSetFileProblem.NOT_A_POSATO_FILE)
        if (version > PauseSetFileLimits.CONTENT_VERSION) {
            return PauseSetJsonResult.Refused(PauseSetFileProblem.NEWER_VERSION)
        }
        if (version < 1) {
            return PauseSetJsonResult.Refused(PauseSetFileProblem.NOT_A_POSATO_FILE)
        }
        return readContents(root)
    }

    private fun readContents(root: JsonObject): PauseSetJsonResult {
        val rawSets = root["sets"] as? JsonArray ?: return refused(PauseSetFileProblem.INVALID_CONTENTS)
        val rawSchedules = root["schedules"] as? JsonArray ?: return refused(PauseSetFileProblem.INVALID_CONTENTS)
        if (rawSets.size > SyncFormatLimits.MAX_PAUSE_SETS || rawSchedules.size > ScheduleLimits.MAX_SCHEDULES) {
            return refused(PauseSetFileProblem.OVER_LIMIT)
        }
        val sets = rawSets.map { element -> readSet(element) ?: return refused(PauseSetFileProblem.INVALID_CONTENTS) }
        val refs = sets.map(FileSet::ref)
        if (refs.distinct().size != refs.size || sets.count(FileSet::first) > 1) {
            return refused(PauseSetFileProblem.INVALID_CONTENTS)
        }
        if (sets.any { set -> set.domains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT } ||
            sets.flatMapTo(mutableSetOf(), FileSet::domains).size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT
        ) {
            return refused(PauseSetFileProblem.OVER_LIMIT)
        }
        val defaultElement = root["defaultSet"]
        val defaultSet = when {
            defaultElement == null || defaultElement is JsonNull -> null
            defaultElement is JsonPrimitive && defaultElement.isString && defaultElement.content in refs -> defaultElement.content
            else -> return refused(PauseSetFileProblem.INVALID_CONTENTS)
        }
        val schedules = rawSchedules.map { element ->
            readSchedule(element)?.takeIf { schedule -> schedule.set in refs } ?: return refused(PauseSetFileProblem.INVALID_CONTENTS)
        }
        return PauseSetJsonResult.Read(PauseSetFile(sets, defaultSet, schedules))
    }

    private fun readSet(element: JsonElement): FileSet? {
        val set = element as? JsonObject ?: return null
        val ref = set.string("ref")?.takeIf { ref -> ref.isNotEmpty() && ref.length <= PauseSetFileLimits.MAX_REF_LENGTH } ?: return null
        val first = (set["first"] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.content?.toBooleanStrictOrNull() ?: return null
        val nameElement = set["name"]
        val name = when {
            nameElement == null || nameElement is JsonNull -> if (first) null else return null
            else -> normalizedName((nameElement as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: return null) ?: return null
        }
        val rawDomains = set["domains"] as? JsonArray ?: return null
        if (rawDomains.size > ExactDomainPolicyLimits.MAX_DOMAIN_COUNT) {
            return null
        }
        val domains = rawDomains.map { domain ->
            val text = (domain as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: return null
            ExactDomain.restore(text) ?: return null
        }.distinct()
        return FileSet(ref, name, first, domains)
    }

    private fun readSchedule(element: JsonElement): FileSchedule? {
        val schedule = element as? JsonObject ?: return null
        val name = normalizedName(schedule.string("name") ?: return null) ?: return null
        val plan = FileSchedule(
            name = name,
            weekdays = schedule.int("weekdays") ?: return null,
            startMinute = schedule.int("start") ?: return null,
            endMinute = schedule.int("end") ?: return null,
            set = schedule.string("set") ?: return null,
        )
        return plan.takeIf(FileSchedule::isValid)
    }

    private fun refused(problem: PauseSetFileProblem): PauseSetJsonResult {
        return PauseSetJsonResult.Refused(problem)
    }
}

/** A name as Posato stores it: trimmed, in NFC, and within the wire's rules; null when it breaks them. */
internal fun normalizedName(raw: String): String? {
    return normalizeApplicationPolicyNameNfc(raw.trim()).takeIf(ScheduleWireRules::isValidName)
}

private fun FileSchedule.isValid(): Boolean {
    val length = (endMinute - startMinute + MINUTES_A_DAY) % MINUTES_A_DAY
    return weekdays in 1..ALL_WEEKDAYS &&
        startMinute in 0 until MINUTES_A_DAY &&
        endMinute in 0 until MINUTES_A_DAY &&
        length >= ScheduleLimits.MIN_MINUTES
}

private const val MINUTES_A_DAY: Int = 1440
private const val ALL_WEEKDAYS: Int = 0b1111111

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
}

private fun JsonObject.int(key: String): Int? {
    return (this[key] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.content?.toIntOrNull()
}

/** The deepest nesting of arrays and objects outside strings, counted without parsing. */
private fun nestingDepth(bytes: ByteArray): Int {
    var depth = 0
    var deepest = 0
    var inString = false
    var escaped = false
    for (byte in bytes) {
        val char = byte.toInt().toChar()
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '[' || char == '{' -> {
                depth++
                deepest = maxOf(deepest, depth)
            }
            char == ']' || char == '}' -> depth--
        }
    }
    return deepest
}
