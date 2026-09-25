package app.posato.control.core

/**
 * Reads a generic password from the login Keychain with `security`. The item's name comes from `local.properties`;
 * the value stays in memory and is only ever piped into typing, never printed, logged, or stored.
 */
fun readKeychainSecret(
    context: RunContext,
    serviceKey: ConfigurationKey,
    accountKey: ConfigurationKey,
    purpose: String
): String {
    val service = context.configuration.require(serviceKey, ErrorCode.COMMAND_FAILED, purpose)
    val account = context.configuration.value(accountKey)
    val command = buildList {
        addAll(listOf("/usr/bin/security", "find-generic-password", "-s", service))
        account?.let { addAll(listOf("-a", it)) }
        add("-w")
    }
    return context.subprocess.run(command)
        .requireSuccess(
            ErrorCode.COMMAND_FAILED,
            "Reading the Keychain item for $purpose",
            "Store it once with `security add-generic-password -s $service -w`.",
        )
        .stdout
        .trimEnd('\n')
}
