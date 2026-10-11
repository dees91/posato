package app.posato.linux.helper

/** Which running processes a pause ends: only the person's own, and only the chosen programs. */
internal object ProcessMatch {
    private const val ROOT_UID = 0

    /** A matcher ending in `/` is a snap's directory, which holds every revision of that snap. */
    fun shouldEnd(
        executable: String,
        ownerUid: Int,
        personUid: Int,
        matchers: List<String>,
    ): Boolean {
        if (ownerUid == ROOT_UID || ownerUid != personUid) return false
        return matchers.any { matcher -> if (matcher.endsWith("/")) executable.startsWith(matcher) else executable == matcher }
    }
}
