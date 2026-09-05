package app.posato.desktop.macos

internal fun interface MacOsApplicationCommands {
    fun configureApplications(
        requirements: List<ByteArray>,
        sessionEndEpochMilliseconds: Long?,
    ): ApplicationEnforcementResponse
}
