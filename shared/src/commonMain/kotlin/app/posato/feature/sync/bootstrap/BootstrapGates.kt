package app.posato.feature.sync.bootstrap

internal sealed interface BindingGate {
    data class Use(
        val binding: AccountBinding
    ) : BindingGate

    data class Stop(
        val result: BootstrapResult
    ) : BindingGate
}

internal sealed interface ZoneGate {
    data object Proceed : ZoneGate

    data class Stop(
        val result: BootstrapResult
    ) : ZoneGate
}

internal sealed interface ItemCheck {
    data class Valid(
        val decoded: DecodedKeyItem
    ) : ItemCheck

    data object Missing : ItemCheck

    data object Retryable : ItemCheck

    data object ActionRequired : ItemCheck
}

internal fun mapStoreFailure(reason: BootstrapStoreFailure): BootstrapResult {
    return when (reason) {
        BootstrapStoreFailure.CORRUPTION -> BootstrapResult.ActionRequired
        BootstrapStoreFailure.STORAGE_FAILURE -> BootstrapResult.Retryable
    }
}
