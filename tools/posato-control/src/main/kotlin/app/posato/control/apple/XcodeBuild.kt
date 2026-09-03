package app.posato.control.apple

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import app.posato.control.core.Target
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

class XcodeBuild(
    private val context: RunContext
) {
    fun appProduct(
        target: Target,
        configuration: String
    ): Path = context.layout.derivedData(
        target.id,
    ).resolve("Build").resolve("Products").resolve("$configuration-${sdkSuffix(target)}").resolve("Posato.app")

    fun driverDerivedData(target: Target): Path = context.layout.derivedData("driver-${target.id}")

    fun driverTestRun(target: Target): Path? = driverDerivedData(target).resolve("Build").resolve("Products").takeIf { it.exists() }
        ?.listDirectoryEntries("*.xctestrun")?.maxByOrNull { Files.getLastModifiedTime(it) }

    fun buildApp(
        target: Target,
        configuration: String,
        udid: String?
    ): Path {
        val command = buildList {
            addAll(
                listOf("/usr/bin/xcodebuild", "-project", context.layout.iosProject.toString(), "-scheme", "iosApp", "-configuration", configuration),
            )
            addAll(destination(target, udid))
            addAll(listOf("-derivedDataPath", context.layout.derivedData(target.id).toString()))
            addAll(signing(target))
            add("build")
        }
        run(command, "Building the iOS application")
        val product = appProduct(target, configuration)
        if (!product.exists()) {
            throw ControlException(
                ErrorCode.BUILD_FAILED,
                "xcodebuild finished but ${context.layout.relativize(product)} is missing.",
            )
        }
        return product
    }

    fun buildDriver(
        target: Target,
        udid: String?
    ): Path {
        val command = buildList {
            addAll(listOf("/usr/bin/xcodebuild", "-project", context.layout.driverProject.toString(), "-scheme", "PosatoDriver"))
            addAll(destination(target, udid))
            addAll(listOf("-derivedDataPath", driverDerivedData(target).toString()))
            addAll(signing(target))
            add("build-for-testing")
        }
        run(command, "Building the iOS driver")
        return driverTestRun(target)
            ?: throw ControlException(ErrorCode.BUILD_FAILED, "xcodebuild finished but no .xctestrun file was produced for the driver.")
    }

    fun testWithoutBuilding(
        testRun: Path,
        udid: String,
        resultBundle: Path,
        environment: Map<String, String>,
        log: Path
    ): Int {
        val command = listOf(
            "/usr/bin/xcodebuild",
            "test-without-building",
            "-xctestrun",
            testRun.toString(),
            "-destination",
            "id=$udid",
            "-resultBundlePath",
            resultBundle.toString(),
            "-only-testing:PosatoDriverUITests/DriverTests/testRunScenario",
        )
        val output = context.subprocess.run(command, environment = environment.mapKeys { (key, _) -> "TEST_RUNNER_$key" }, timeout = TEST_TIMEOUT)
        Files.createDirectories(log.parent)
        Files.writeString(log, output.stdout + "\n--- stderr ---\n" + output.stderr)
        return output.exitCode
    }

    private fun destination(
        target: Target,
        udid: String?
    ): List<String> = when (target) {
        Target.SIMULATOR -> listOf("-destination", if (udid != null) "platform=iOS Simulator,id=$udid" else "generic/platform=iOS Simulator")
        Target.DEVICE -> listOf("-destination", "generic/platform=iOS")
        Target.DESKTOP -> throw ControlException(ErrorCode.UNSUPPORTED_ON_TARGET, "xcodebuild does not build the desktop application.")
    }

    private fun signing(target: Target): List<String> = when (target) {
        Target.SIMULATOR -> {
            listOf("CODE_SIGNING_ALLOWED=NO", "CODE_SIGNING_REQUIRED=NO")
        }

        Target.DEVICE -> {
            val team = context.configuration.require(
                ConfigurationKey.DEVELOPMENT_TEAM,
                ErrorCode.DEVELOPMENT_TEAM_MISSING,
                "Signing for a physical iPhone",
            )
            listOf("-allowProvisioningUpdates", "DEVELOPMENT_TEAM=$team", "CODE_SIGN_STYLE=Automatic")
        }

        Target.DESKTOP -> {
            emptyList()
        }
    }

    private fun sdkSuffix(target: Target): String = if (target == Target.DEVICE) "iphoneos" else "iphonesimulator"

    private fun run(
        command: List<String>,
        what: String
    ) {
        val output = context.subprocess.run(command, workingDirectory = context.layout.root, timeout = BUILD_TIMEOUT)
        if (!output.succeeded) {
            val errors = output.stdout.lines().filter { it.contains("error:") || it.contains("** BUILD FAILED") }.takeLast(ERROR_LINES)
            throw ControlException(
                ErrorCode.BUILD_FAILED,
                "$what failed with exit code ${output.exitCode}.\n" + errors.joinToString("\n"),
                "Rerun with --verbose to keep the full xcodebuild transcript.",
            )
        }
    }

    private companion object {
        const val ERROR_LINES = 12
        val BUILD_TIMEOUT: Duration = Duration.ofMinutes(30)
        val TEST_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
