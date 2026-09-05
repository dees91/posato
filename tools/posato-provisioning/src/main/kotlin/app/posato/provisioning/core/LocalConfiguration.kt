package app.posato.provisioning.core

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

enum class ConfigurationKey(
    val propertyName: String,
    val environmentName: String
) {
    ASC_KEY_ID("posato.asc.keyId", "POSATO_ASC_KEY_ID"),
    ASC_ISSUER_ID("posato.asc.issuerId", "POSATO_ASC_ISSUER_ID"),
    ASC_PRIVATE_KEY_PATH("posato.asc.privateKeyPath", "POSATO_ASC_PRIVATE_KEY_PATH"),
    DEVELOPMENT_TEAM("posato.apple.developmentTeam", "POSATO_APPLE_DEVELOPMENT_TEAM"),
    MACOS_SYNC_PROVISIONING_PROFILE("posato.macos.syncProvisioningProfile", "POSATO_MACOS_SYNC_PROVISIONING_PROFILE"),
}

enum class ConfigurationSource { ENVIRONMENT, LOCAL_PROPERTIES, ABSENT }

/**
 * The ignored `local.properties` file and the environment, read the same way the verification driver reads them so a
 * provisioned checkout configures both tools once. The environment wins so a one-off run can override a checkout.
 */
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
        purpose: String
    ): String = value(key)
        ?: throw ProvisioningException(
            ErrorCode.CONFIGURATION_MISSING,
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
