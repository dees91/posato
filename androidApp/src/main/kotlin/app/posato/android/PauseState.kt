package app.posato.android

import android.content.Context

/** What a pause enforces on this phone, kept so the services restore it after the process or the phone restarts. */
internal data class AppliedPause(
    val sessionId: String,
    val endEpochMillis: Long,
    val domains: Set<String>,
    val packages: Set<String>,
)

internal class PauseState(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun applied(): AppliedPause? {
        val sessionId = preferences.getString(SESSION, null) ?: return null
        return AppliedPause(
            sessionId,
            preferences.getLong(END, 0),
            preferences.getStringSet(DOMAINS, emptySet()).orEmpty(),
            preferences.getStringSet(PACKAGES, emptySet()).orEmpty(),
        )
    }

    fun expiredSessionId(): String? {
        return preferences.getString(EXPIRED, null)
    }

    fun apply(pause: AppliedPause) {
        preferences.edit()
            .putString(SESSION, pause.sessionId)
            .putLong(END, pause.endEpochMillis)
            .putStringSet(DOMAINS, pause.domains)
            .putStringSet(PACKAGES, pause.packages)
            .remove(EXPIRED.takeIf { preferences.getString(EXPIRED, null) == pause.sessionId } ?: NONE)
            .commit()
    }

    fun clear(expired: Boolean) {
        val sessionId = preferences.getString(SESSION, null)
        val editor = preferences.edit().remove(SESSION).remove(END).remove(DOMAINS).remove(PACKAGES)
        if (expired && sessionId != null) editor.putString(EXPIRED, sessionId)
        editor.commit()
    }

    fun acknowledge(sessionId: String) {
        if (expiredSessionId() == sessionId) preferences.edit().remove(EXPIRED).commit()
    }

    private companion object {
        const val FILE = "pause-state"
        const val SESSION = "session"
        const val END = "end"
        const val DOMAINS = "domains"
        const val PACKAGES = "packages"
        const val EXPIRED = "expired"
        const val NONE = "-"
    }
}
