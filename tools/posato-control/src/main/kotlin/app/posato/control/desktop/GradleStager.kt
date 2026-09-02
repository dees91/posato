package app.posato.control.desktop

import app.posato.control.core.ConfigurationKey
import app.posato.control.core.ErrorCode
import app.posato.control.core.RunContext
import java.time.Duration

class GradleStager(
    private val context: RunContext
) {
    fun stage(
        signingIdentity: String?,
        verify: Boolean
    ) {
        val identity = signingIdentity ?: context.configuration.value(ConfigurationKey.MACOS_SIGNING_IDENTITY)
        val command = buildList {
            add(context.layout.gradlew.toString())
            add(":desktopApp:stageMacOsDevelopmentPackage")
            if (verify) add(":desktopApp:verifyMacOsDevelopmentPackaging")
            add("--console=plain")
            if (identity != null) add("-PposatoMacOsSigningIdentity=$identity")
        }
        context.subprocess.run(command, workingDirectory = context.layout.root, timeout = GRADLE_TIMEOUT)
            .requireSuccess(
                ErrorCode.BUILD_FAILED,
                "Staging the desktop package",
                "Run ./gradlew :desktopApp:stageMacOsDevelopmentPackage manually to inspect the failure.",
            )
    }

    private companion object {
        val GRADLE_TIMEOUT: Duration = Duration.ofMinutes(20)
    }
}
