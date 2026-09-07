package app.posato.prototype.model

internal fun reduceSession(
    state: PrototypeState,
    action: SessionAction
): PrototypeState {
    return when (action) {
        SessionAction.OpenSetup -> openSetup(state)
        SessionAction.ReturnToSession -> returnToSession(state)
        SessionAction.Review -> reviewSession(state)
        SessionAction.Start -> startSession(state)
        SessionAction.OpenBlocked -> openBlocked(state)
        SessionAction.OpenPosato -> openPosato(state)
        SessionAction.RequestEarlyEnd -> requestEarlyEnd(state)
        SessionAction.CancelEarlyEnd -> cancelEarlyEnd(state)
        SessionAction.ConfirmEarlyEnd -> endSession(state, confirmed = true)
        SessionAction.Expire -> endSession(state, confirmed = false)
    }
}

internal fun sessionOverview(state: PrototypeState): PrototypeSurface {
    return if (state.session.active) PrototypeSurface.Active else PrototypeSurface.Home
}

private fun openSetup(state: PrototypeState): PrototypeState {
    if (!state.onboardingComplete || state.session.active) return state.blocked("Finish setup and end any active session first.")

    return state.copy(
        surface = PrototypeSurface.SessionSetup,
        session = state.session.copy(endsAt = PrototypeClock.resolvedEnd(state.session.durationMinutes)),
    ).withOutcome("Choose a duration before reviewing the session.")
}

private fun returnToSession(state: PrototypeState): PrototypeState {
    if (!state.onboardingComplete) return state.blocked("Finish setup first.")

    return state.copy(surface = sessionOverview(state)).withOutcome("Your session overview is open.")
}

private fun reviewSession(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.SessionSetup && state.surface != PrototypeSurface.SessionReview) {
        return state.blocked("Open session setup before reviewing it.")
    }
    val issues = state.reviewIssues()

    return state.copy(surface = PrototypeSurface.SessionReview).withOutcome(
        if (issues.isEmpty()) "The end time and effective local items are ready for confirmation." else "${issues.size} items need attention.",
        if (issues.isEmpty()) OutcomeTone.Success else OutcomeTone.Warning,
    )
}

private fun startSession(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.SessionReview) return state.blocked("Review the session before starting it.")
    if (state.reviewIssues().isNotEmpty()) return state.blocked("Resolve every action-required item shown in the review.")

    return state.copy(
        surface = PrototypeSurface.Active,
        session = state.session.copy(active = true, endsAt = state.session.endsAt ?: PrototypeClock.resolvedEnd(state.session.durationMinutes)),
    ).pending("The session is active on this device. Its intent is pending synchronization.")
}

private fun openBlocked(state: PrototypeState): PrototypeState {
    if (!state.session.active || state.permission != PrototypePermission.Granted || state.effectiveItemCount() == 0) {
        return state.blocked("A locally enforceable active session is required to show the paused presentation.")
    }

    return state.copy(surface = PrototypeSurface.Blocked).withOutcome("A selected item is paused. No attempted URL or usage event was stored.")
}

private fun openPosato(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.Blocked) return state.blocked("Open Posato is offered from the paused presentation.")

    return state.copy(surface = PrototypeSurface.Active).withOutcome("Posato shows the truthful active-session state.")
}

private fun requestEarlyEnd(state: PrototypeState): PrototypeState {
    if (!state.session.active) return state.blocked("There is no active session to end.")

    return state.copy(surface = PrototypeSurface.EarlyEnd).withOutcome("Early termination requires a clear confirmation.", OutcomeTone.Warning)
}

private fun cancelEarlyEnd(state: PrototypeState): PrototypeState {
    if (state.surface != PrototypeSurface.EarlyEnd) return state.blocked("The early-end confirmation is not open.")

    return state.copy(surface = PrototypeSurface.Active).withOutcome("The session remains active until ${state.session.endsAt}.")
}

private fun endSession(
    state: PrototypeState,
    confirmed: Boolean
): PrototypeState {
    if (!state.session.active) return state.blocked("There is no active session to end.")
    if (confirmed && state.surface != PrototypeSurface.EarlyEnd) return state.blocked("Open the early-end confirmation first.")
    val message = if (confirmed) {
        "Restrictions stopped on this device. The early end is pending synchronization."
    } else {
        "The selected end time passed. Local restrictions were removed and the state is pending synchronization."
    }

    return state.copy(surface = PrototypeSurface.Home, session = state.session.copy(active = false, endsAt = null)).pending(message)
}
