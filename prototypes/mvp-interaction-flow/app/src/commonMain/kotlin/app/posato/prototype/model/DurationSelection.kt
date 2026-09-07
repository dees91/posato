package app.posato.prototype.model

internal fun selectDuration(
    state: PrototypeState,
    input: String
): PrototypeState {
    if (state.surface != PrototypeSurface.SessionSetup) return state.blocked("Open session setup before changing the duration.")
    val minutes = input.trim().toIntOrNull()
    if (minutes == null || minutes !in PrototypeClock.MINIMUM_MINUTES..PrototypeClock.MAXIMUM_MINUTES) {
        return state.blocked("Choose a whole number from 5 to 1,440 minutes.")
    }
    val end = PrototypeClock.resolvedEnd(minutes)

    return state.copy(session = state.session.copy(durationMinutes = minutes, endsAt = end))
        .withOutcome("The proposed end time is $end. Review your selected items next.")
}
