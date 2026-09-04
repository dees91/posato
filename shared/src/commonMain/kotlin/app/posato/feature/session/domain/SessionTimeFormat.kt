package app.posato.feature.session.domain

internal interface SessionTimeFormat {
    fun formatTime(epochMillis: Long): String
}
