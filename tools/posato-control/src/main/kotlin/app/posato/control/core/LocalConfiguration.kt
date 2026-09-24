package app.posato.control.core

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

enum class ConfigurationKey(
    val propertyName: String,
    val environmentName: String
) {
    DEVELOPMENT_TEAM("posato.apple.developmentTeam", "POSATO_APPLE_DEVELOPMENT_TEAM"),
    MACOS_SIGNING_IDENTITY("posato.macos.signingIdentity", "POSATO_MACOS_SIGNING_IDENTITY"),
    MACOS_SYNC_PROVISIONING_PROFILE("posato.macos.syncProvisioningProfile", "POSATO_MACOS_SYNC_PROVISIONING_PROFILE"),
    SIMULATOR("posato.control.simulator", "POSATO_CONTROL_SIMULATOR"),
    DEVICE("posato.control.device", "POSATO_CONTROL_DEVICE"),
    DEVICE_PASSCODE_KEYCHAIN_SERVICE("posato.control.devicePasscodeKeychainService", "POSATO_CONTROL_DEVICE_PASSCODE_KEYCHAIN_SERVICE"),
    DEVICE_PASSCODE_KEYCHAIN_ACCOUNT("posato.control.devicePasscodeKeychainAccount", "POSATO_CONTROL_DEVICE_PASSCODE_KEYCHAIN_ACCOUNT"),
    VM_PRIMARY_GOLDEN("posato.vm.primaryGolden", "POSATO_VM_PRIMARY_GOLDEN"),
    VM_PEER_GOLDEN("posato.vm.peerGolden", "POSATO_VM_PEER_GOLDEN"),
    VM_ADMIN_KEYCHAIN_SERVICE("posato.vm.adminKeychainService", "POSATO_VM_ADMIN_KEYCHAIN_SERVICE"),
    VM_ADMIN_KEYCHAIN_ACCOUNT("posato.vm.adminKeychainAccount", "POSATO_VM_ADMIN_KEYCHAIN_ACCOUNT"),
}

enum class ConfigurationSource { ENVIRONMENT, LOCAL_PROPERTIES, ABSENT }

class LocalConfiguration(
    private val properties: Map<String, String>,
    private val environment: Map<String, String>,
) {
    fun value(key: ConfigurationKey): String? = environment[key.environmentName]?.takeIf { it.isNotBlank() }
        ?: properties[key.propertyName]?.takeIf { it.isNotBlank() }

    fun source(key: ConfigurationKey): ConfigurationSource = when {
        !environment[key.environmentName].isNullOrBlank() -> ConfigurationSource.ENVIRONMENT
        !properties[key.propertyName].isNullOrBlank() -> ConfigurationSource.LOCAL_PROPERTIES
        else -> ConfigurationSource.ABSENT
    }

    fun require(
        key: ConfigurationKey,
        code: ErrorCode,
        purpose: String
    ): String = value(key)
        ?: throw ControlException(
            code,
            "$purpose needs the ${key.propertyName} value.",
            "Add ${key.propertyName}=<value> to the ignored local.properties file or export ${key.environmentName}.",
        )

    companion object {
        fun load(
            layout: RepoLayout,
            environment: Map<String, String> = System.getenv()
        ): LocalConfiguration = LocalConfiguration(parseProperties(layout.localProperties), environment)

        fun parseProperties(file: Path): Map<String, String> {
            if (!file.exists()) return emptyMap()
            return parseProperties(file.readLines())
        }

        fun parseProperties(lines: List<String>): Map<String, String> = lines
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("!") }
            .mapNotNull { line ->
                val separator = line.indexOfFirst { it == '=' || it == ':' }
                if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }
            .toMap()
    }
}
