package app.posato.windows.helper

/** The account that installed Posato, which the service serves and whose processes and connections a pause affects. */
internal data class HelperConfig(
    val user: String,
    val sid: String,
) {
    fun encode(): String = "user=$user\nsid=$sid\n"

    companion object {
        fun decode(text: String): HelperConfig? {
            val values = text.lines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim() to value.trim() }
            val user = values["user"]?.takeIf(InstallingUser::isAccountName) ?: return null
            val sid = values["sid"]?.takeIf(InstallingUser::isPerson) ?: return null
            return HelperConfig(user, sid)
        }
    }
}

internal object InstallingUser {
    private const val MAX_NAME_LENGTH = 256

    /**
     * A person's account: a local or domain user (`S-1-5-21-…`) or a Microsoft Entra user (`S-1-12-1-…`), each with
     * its relative identifier. The system, service, and virtual accounts and groups are never served.
     */
    private val person = Regex("S-1-5-21(-[0-9]{1,10}){4}|S-1-12-1(-[0-9]{1,10}){4}")

    /** An account's name as recorded for reading: `DOMAIN\name`, or the SID when no name could be looked up. */
    fun isAccountName(name: String): Boolean = name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && name.none { it.isISOControl() }

    fun isPerson(sid: String): Boolean = person.matches(sid)
}
