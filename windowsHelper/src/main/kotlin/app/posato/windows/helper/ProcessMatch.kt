package app.posato.windows.helper

/** Which running processes a pause ends: only the installing user's own, and only the chosen programs. */
internal object ProcessMatch {
    fun shouldEnd(
        executable: String,
        ownerSid: String,
        userSid: String,
        matchers: List<String>,
    ): Boolean {
        if (ownerSid in WellKnownSid.SERVICE_ACCOUNTS || ownerSid != userSid) return false
        return matchers.any { matcher -> executable.equals(matcher, ignoreCase = true) }
    }
}

/** Only the installing user and the system may talk to the service. */
internal object PipeCaller {
    fun isAllowed(
        callerSid: String?,
        userSid: String,
    ): Boolean {
        return callerSid != null && (callerSid == WellKnownSid.SYSTEM || callerSid == userSid)
    }
}

internal object WellKnownSid {
    const val SYSTEM = "S-1-5-18"
    const val ADMINISTRATORS = "S-1-5-32-544"
    val SERVICE_ACCOUNTS = setOf(SYSTEM, "S-1-5-19", "S-1-5-20")
}
