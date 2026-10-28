package app.posato.control.cli

import app.posato.control.apple.Simctl
import app.posato.control.backend.Backend
import app.posato.control.backend.LaunchOptions
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import app.posato.control.model.Actions
import app.posato.control.model.Query
import app.posato.control.model.Step
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile

/** App Store artwork made from the running application. */
class StoreCommand : CliktCommand(name = "store") {
    override fun help(context: Context): String = "Make App Store artwork from the running application: capture a locale's screenshot set."

    override fun run() = Unit
}

/**
 * Captures the four App Store screenshots of one store locale on a Simulator: Paused websites, Session duration,
 * Schedules, and About Posato. Every control is pressed by its id, so one path serves every language; the language
 * comes from `docs/store/<locale>/capture.json`. `--prepare` first installs fresh and sets up the synthetic set and
 * schedule in English, whose labels the onboarding scenario and the schedule editor flow read.
 */
class StoreCaptureCommand : ControlCommand("capture", "Simulator: capture a store locale's four App Store screenshots as RGB PNGs.") {
    private val locale by option("--locale", help = "Store locale directory under docs/store, such as en-US or pl-PL.").required()
    private val out by option("--out", help = "Directory for the four PNGs; created when missing.").path(canBeFile = false).required()
    private val prepare by option(
        "--prepare",
        help = "First reset Posato and set up the synthetic websites, the optional set name, and the schedule (wipes local state).",
    ).flag()

    override fun execute(session: Session): JsonElement {
        if (session.target() != Target.SIMULATOR) {
            throw ControlException(
                ErrorCode.UNSUPPORTED_ON_TARGET,
                "store capture runs on an iOS Simulator, whose status bar and screen simctl controls.",
                "Pass -t sim --udid <udid>.",
            )
        }
        val settings = StoreCaptureSettings.read(session.layout.root.resolve("docs").resolve("store").resolve(locale))
        val backend = session.backend()
        val simctl = Simctl(session.context)
        val capture = StoreCapture(backend, simctl, session.context, out.toAbsolutePath())
        if (prepare) capture.prepare(settings, session.layout.toolDirectory.resolve(FIRST_INSTALL_SCENARIO))
        val result = capture.capture(settings)
        return buildJsonObject {
            put("locale", locale)
            put("language", settings.language)
            put("udid", result.udid)
            put("prepared", prepare)
            put("statusBarTime", result.time)
            put("out", session.layout.relativize(out.toAbsolutePath()))
            putJsonArray("files") { result.files.forEach { add(it.fileName.toString()) } }
        }
    }

    private companion object {
        val FIRST_INSTALL_SCENARIO: Path = Path.of("fixtures", "scenarios", "first-install-skip.json")
    }
}

/** What differs between store locales: the language Posato is launched in and the synthetic names it shows. */
@Serializable
internal data class StoreCaptureSettings(
    /** The `-AppleLanguages` value, such as `pl`. */
    val language: String,
    /** The `-AppleLocale` value, such as `pl_PL`. */
    val locale: String,
    val scheduleName: String,
    /** A name for the first set; without one the set keeps the name the app gives it in its language. */
    val setName: String? = null,
) {
    val launchArguments: List<String>
        get() = appleLanguageArguments(language, locale)

    companion object {
        const val FILE = "capture.json"

        fun read(directory: Path): StoreCaptureSettings {
            val file = directory.resolve(FILE)
            if (!file.isRegularFile()) {
                throw ControlException(
                    ErrorCode.USAGE,
                    "No ${directory.fileName}/$FILE under docs/store.",
                    "Pass a directory under docs/store, such as en-US, or add its $FILE.",
                )
            }
            return try {
                ControlJson.pretty.decodeFromString(serializer(), Files.readString(file))
            } catch (exception: SerializationException) {
                throw ControlException(ErrorCode.USAGE, "$file is not valid: ${exception.message}", cause = exception)
            }
        }
    }
}

/** Launch arguments that start Posato in [language] and [locale] while the Simulator keeps its own. */
private fun appleLanguageArguments(
    language: String,
    locale: String,
): List<String> = listOf("-AppleLanguages", "($language)", "-AppleLocale", locale)

internal class StoreCaptureResult(
    val udid: String,
    val time: String,
    val files: List<Path>,
)

private class StoreCapture(
    private val backend: Backend,
    private val simctl: Simctl,
    private val context: RunContext,
    private val out: Path,
) {
    /** Installs fresh, skips onboarding, fills the first set, optionally renames it, and adds the weekday schedule. */
    fun prepare(
        settings: StoreCaptureSettings,
        onboarding: Path,
    ) {
        val udid = launch(appleLanguageArguments(ENGLISH, ENGLISH_LOCALE), fresh = true)
        darkAppearance(udid)
        failIfStepFailed(backend.runScenario(parseScenario(Files.readString(onboarding))))
        val back = FlowControls.pauseSetsBack(apple = true)
        val steps = mutableListOf(
            FlowControls.buttonId("nav_pause_sets"),
            FlowSteps.tap(SET_ROW),
            FlowSteps.waitFor(Query(role = FlowSteps.ROLE_TEXT_FIELD)),
            FlowSteps.typeInto(WEBSITES, submit = true),
            FlowControls.buttonId("sync_folder_code_done", optional = true, timeoutSeconds = SHORT_SECONDS),
            FlowSteps.tap(back),
            FlowSteps.waitFor(SET_ROW),
        )
        settings.setName?.let { name ->
            steps += listOf(
                Step(action = Actions.SWIPE_LEFT, query = SET_ROW),
                FlowSteps.tap(Query(id = "action_rename")),
                FlowSteps.typeInto(name, submit = false),
                FlowControls.alertButton("action_save_short", "Save", apple = true),
                FlowSteps.waitFor(Query(textContains = "$name, ")),
            )
        }
        FlowSteps.run(backend, steps)
        ScheduleEditor(backend, Target.SIMULATOR).add(
            ScheduleRequest(settings.scheduleName, WheelTime.parse(SCHEDULE_START), WheelTime.parse(SCHEDULE_END), ScheduleDays.parse("weekdays")),
        )
    }

    /**
     * Launches in the locale's language, sets the status bar to the current time, so it matches Session's "Ends at",
     * and captures the four screens. The status bar override is cleared even when a step fails.
     */
    fun capture(settings: StoreCaptureSettings): StoreCaptureResult {
        Files.createDirectories(out)
        val udid = launch(settings.launchArguments, fresh = false)
        darkAppearance(udid)
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        simctl(udid, "status_bar", udid, "override", "--time", time, "--batteryState", "discharging", "--batteryLevel", "100", "--wifiBars", "3")
        val files = mutableListOf<Path>()
        try {
            show(FlowControls.buttonId("action_start_a_session"), FlowSteps.waitFor(FlowControls.byId("session_setup_continue")))
            files.add(shoot(udid, "02-session-duration"))
            show(
                FlowControls.buttonId("session_title"),
                FlowControls.buttonId("nav_pause_sets"),
                FlowSteps.tap(SET_ROW),
            )
            files.add(shoot(udid, "01-paused-websites"))
            // From a set, the first press returns Pause sets to its list and the second opens Schedules.
            show(
                FlowControls.buttonId("nav_schedules"),
                FlowControls.buttonId("nav_schedules"),
                FlowSteps.waitFor(FlowControls.byId("action_add_schedule")),
            )
            files.add(shoot(udid, "03-schedules"))
            // About hides the tab bar and its Back is a spoken phrase, so a relaunch returns to Session instead.
            launch(settings.launchArguments, fresh = false)
            show(FlowControls.buttonId("about_title"))
            files.add(shoot(udid, "04-about-posato"))
        } finally {
            simctl(udid, "status_bar", udid, "clear")
        }
        return StoreCaptureResult(udid, time, files.sortedBy { it.fileName.toString() })
    }

    private fun launch(
        arguments: List<String>,
        fresh: Boolean,
    ): String = backend.launch(LaunchOptions(fresh = fresh, arguments = arguments)).udid
        ?: throw ControlException(ErrorCode.NO_BOOTED_SIMULATOR, "The launch reported no Simulator.")

    private fun darkAppearance(udid: String) = simctl(udid, "ui", udid, "appearance", "dark")

    private fun show(vararg steps: Step) = FlowSteps.run(backend, steps.toList() + FlowSteps.sleep(SETTLE_SECONDS))

    /** `simctl io screenshot` keeps an alpha channel, which App Store Connect refuses, so the pixels are rewritten as RGB. */
    private fun shoot(
        udid: String,
        name: String,
    ): Path {
        val raw = context.recordArtifact(context.artifactPath("store-raw", "$name.png"))
        simctl.screenshot(udid, raw)
        val source = ImageIO.read(raw.toFile())
            ?: throw ControlException(ErrorCode.COMMAND_FAILED, "The capture at ${context.layout.relativize(raw)} is not a readable image.")
        val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        rgb.setRGB(0, 0, source.width, source.height, source.getRGB(0, 0, source.width, source.height, null, 0, source.width), 0, source.width)
        val target = out.resolve("$name.png")
        if (!ImageIO.write(rgb, "png", target.toFile())) throw ControlException(ErrorCode.COMMAND_FAILED, "No PNG writer is available.")
        return target
    }

    private fun simctl(
        udid: String,
        vararg arguments: String,
    ) {
        context.subprocess.run(listOf(Simctl.XCRUN, "simctl") + arguments)
            .requireSuccess(ErrorCode.COMMAND_FAILED, "Running simctl ${arguments.first()} on $udid")
    }

    private companion object {
        const val ENGLISH = "en"

        // The schedule editor flow turns 24-hour wheels; en_US would show a 12-hour picker (`observed` 2026-10-28).
        const val ENGLISH_LOCALE = "en_GB"
        const val WEBSITES = "news.example, social.example, video.example"
        const val SCHEDULE_START = "09:00"
        const val SCHEDULE_END = "12:00"
        const val SHORT_SECONDS = 3.0
        const val SETTLE_SECONDS = 1.5
        val SET_ROW = Query(id = "pause_set_row", role = FlowSteps.ROLE_BUTTON)
    }
}
