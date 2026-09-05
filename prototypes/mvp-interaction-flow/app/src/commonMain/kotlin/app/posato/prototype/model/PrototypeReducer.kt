package app.posato.prototype.model

fun reducePrototype(
    state: PrototypeState,
    action: PrototypeAction
): PrototypeState {
    return when (action) {
        ResetPrototype -> PrototypeState(state.platform)
        is SetupAction -> reduceSetup(state, action)
        is SessionAction -> reduceSession(state, action)
        is SetDuration -> selectDuration(state, action.input)
        is SyncAction -> reduceSync(state, action)
        is RecoveryAction -> reduceRecovery(state, action)
        is ItemAction -> reduceItems(state, action)
    }
}
