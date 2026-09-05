package app.posato.provisioning.core

private const val MIN_SECRET_LENGTH = 4
private const val PLACEHOLDER = "<redacted>"

/**
 * Values that must never reach stderr, an envelope, or a file.
 *
 * The registry is seeded from configuration and then grows, because a device identifier or a certificate common name
 * is only known after the command that reads it has already started. Registering a value the moment it is read is
 * what keeps a later failure message from carrying it.
 */
class Redaction {
    private val secrets = LinkedHashSet<String>()

    fun register(value: String?) {
        if (value != null && value.length >= MIN_SECRET_LENGTH) secrets.add(value)
    }

    fun redact(text: String): String = secrets.fold(text) { current, secret -> current.replace(secret, PLACEHOLDER) }
}
