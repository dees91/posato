package app.posato.control.cli

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ConfigurationSource
import app.posato.control.core.ControlException
import app.posato.control.core.ControlJson
import app.posato.control.core.Target
import app.posato.control.core.runsInVirtualMachine
import app.posato.control.desktop.AxBridge
import app.posato.control.model.DoctorCheck
import app.posato.control.model.DoctorReport
import app.posato.control.model.Severity
import app.posato.control.vm.DISK_SPACE_HINT
import app.posato.control.vm.gigabytes
import app.posato.control.vm.lowestFreeSpace
import app.posato.control.vm.minFreeDiskBytes
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonElement
import java.time.Duration
import kotlin.io.path.exists

class DoctorCommand :
    ControlCommand("doctor", "Report whether the toolchain, configuration, permissions, and the selected target (or all targets) are ready.") {
    override val hostDesktopAllowed = true

    private val deep by option("--deep", help = "Also verify the Gradle daemon JVM (slower).").flag()
    private val requestPermissions by option("--request-permissions", help = "Trigger the macOS Accessibility and Screen Recording prompts.").flag()
    private val skipAutomationProbe by option(
        "--skip-automation-probe",
        help = "Skip the device's UI automation probe, a short driver run that otherwise reveals a locked iPhone.",
    ).flag()

    override fun execute(session: Session): JsonElement {
        val checks = mutableListOf<DoctorCheck>()
        checks.addAll(toolchain(session))
        checks.addAll(configuration(session))
        if (requestPermissions) AxBridge(session.context).permissions(request = true)
        val targets = session.options.target?.let { listOf(it) } ?: Target.entries
        targets.forEach { target -> checks.addAll(targetChecks(session, target)) }
        // Only on request: the probe brings the XCTest runner to the front of a phone another session may be driving.
        if (session.options.target == Target.DEVICE && !runsInVirtualMachine() && !skipAutomationProbe) {
            automationProbe(session)?.let(checks::add)
        }
        val ok = checks.none { !it.ok && it.severity == Severity.ERROR.name.lowercase() }
        return ControlJson.pretty.encodeToJsonElement(DoctorReport.serializer(), DoctorReport(ok, checks))
    }

    /**
     * A Tart guest only runs what the host built and copied in (`vm sync`), so it has no Xcode or Gradle wrapper; the
     * build prerequisites are checked by `doctor` on the host, and the guest reports its runtime checks alone.
     */
    private fun toolchain(session: Session): List<DoctorCheck> = if (runsInVirtualMachine()) {
        listOf(sqliteCheck())
    } else {
        buildToolchain(session) + diskSpaceCheck(session) + rosettaCheck(session)
    }

    /**
     * The Intel build runs an x86-64 JDK on the host, which needs Rosetta 2. A macOS update can leave Rosetta out
     * (`observed` once, macOS 27.0.1), and the build then failed only deep inside `checkRuntime` (release 1.4 retro).
     */
    private fun rosettaCheck(session: Session): DoctorCheck {
        val probe = session.context.subprocess.run(listOf("/usr/bin/arch", "-x86_64", "/usr/bin/true"))
        return if (probe.succeeded) {
            DoctorCheck.pass("host.rosetta", "Rosetta 2 runs x86-64 code.")
        } else {
            DoctorCheck.fail(
                "host.rosetta",
                "Rosetta 2 is not installed; x86-64 (Intel) builds cannot run their JDK.",
                "Run `softwareupdate --install-rosetta --agree-to-license` (administrator password).",
                Severity.WARN,
            )
        }
    }

    /** The device backend's automation probe; null when no device backend can be made. */
    private fun automationProbe(session: Session): DoctorCheck? = try {
        session.backend(Target.DEVICE).automationProbe()
    } catch (exception: ControlException) {
        DoctorCheck.fail("device.automation", exception.message ?: exception.code.name, exception.hint, Severity.WARN)
    }

    /** A host that runs out of space breaks a build or a clone midway, long after this check could have said so. */
    private fun diskSpaceCheck(session: Session): DoctorCheck {
        val free = lowestFreeSpace(session.layout.root)
        val detail = "${gigabytes(free.bytes)} free on the volume holding ${free.path}."
        return if (free.bytes >= minFreeDiskBytes()) {
            DoctorCheck.pass("host.diskSpace", detail)
        } else {
            val needed = "A run needs at least ${gigabytes(minFreeDiskBytes())}; `vm create` and guest commands refuse below it."
            DoctorCheck.fail("host.diskSpace", "$detail $needed", DISK_SPACE_HINT, Severity.WARN)
        }
    }

    private fun sqliteCheck(): DoctorCheck = if (java.io.File("/usr/bin/sqlite3").exists()) {
        DoctorCheck.pass("sqlite3", "sqlite3 is available.")
    } else {
        DoctorCheck.fail("sqlite3", "sqlite3 is missing.", "Install the Xcode command line tools.")
    }

    private fun buildToolchain(session: Session): List<DoctorCheck> {
        val checks = mutableListOf<DoctorCheck>()
        val developer = session.context.subprocess.run(listOf("/usr/bin/xcode-select", "-p"))
        checks.add(
            if (developer.succeeded) {
                DoctorCheck.pass(
                    "xcode.path",
                    developer.stdout.trim(),
                )
            } else {
                DoctorCheck.fail("xcode.path", "Xcode is not selected.", "Run xcode-select --install or select Xcode.")
            },
        )
        val version = session.context.subprocess.run(listOf("/usr/bin/xcodebuild", "-version"))
        checks.add(
            if (version.succeeded) {
                DoctorCheck.pass(
                    "xcode.version",
                    version.stdout.lines().first(),
                )
            } else {
                DoctorCheck.fail("xcode.version", "xcodebuild is unavailable.", "Install Xcode and select it with xcode-select -s.")
            },
        )
        checks.add(sqliteCheck())
        checks.add(
            if (session.layout.gradlew.exists()) {
                DoctorCheck.pass(
                    "gradle.wrapper",
                    "Gradle wrapper found.",
                )
            } else {
                DoctorCheck.fail("gradle.wrapper", "gradlew is missing.", "Run the tool from inside the repository checkout.")
            },
        )
        if (deep) checks.add(gradleDaemonCheck(session))
        return checks
    }

    private fun gradleDaemonCheck(session: Session): DoctorCheck {
        val output = session.context.subprocess.run(
            listOf(session.layout.gradlew.toString(), "--version"),
            workingDirectory = session.layout.root,
            timeout = Duration.ofMinutes(5),
        )
        val daemon = output.stdout.lines().firstOrNull { it.startsWith("Daemon JVM") }.orEmpty()
        return if (output.succeeded && daemon.contains("Java 21")) {
            DoctorCheck.pass("gradle.daemonJvm", daemon.trim())
        } else {
            DoctorCheck.fail(
                "gradle.daemonJvm",
                "The Gradle daemon JVM is not Temurin 21: ${daemon.ifBlank {
                    output.stderr.trim()
                }}",
                "Install Eclipse Temurin 21; the build auto-provisions it through the toolchain resolver.",
            )
        }
    }

    private fun configuration(session: Session): List<DoctorCheck> = ConfigurationKey.entries.map { key ->
        val source = session.configuration.source(key)
        if (source == ConfigurationSource.ABSENT) {
            val severity = if (key == ConfigurationKey.DEVELOPMENT_TEAM) Severity.WARN else Severity.INFO
            DoctorCheck.fail(
                "config.${key.propertyName}",
                "${key.propertyName} is not set.",
                "Add it to the ignored local.properties file or export ${key.environmentName}.",
                severity,
            )
        } else {
            DoctorCheck.pass("config.${key.propertyName}", "${key.propertyName} comes from ${source.name.lowercase().replace('_', ' ')}.")
        }
    }

    private fun targetChecks(
        session: Session,
        target: Target
    ): List<DoctorCheck> = try {
        session.backend(target).doctor()
    } catch (exception: ControlException) {
        listOf(DoctorCheck.fail("${target.id}.doctor", exception.message ?: exception.code.name, exception.hint, Severity.WARN))
    }
}
