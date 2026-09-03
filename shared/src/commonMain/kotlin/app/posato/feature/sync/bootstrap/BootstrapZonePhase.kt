package app.posato.feature.sync.bootstrap

internal class BootstrapZonePhase(
    private val cloud: BootstrapCloudPort
) {
    suspend fun ensureZone(
        binding: AccountBinding,
        hasEstablished: Boolean
    ): ZoneGate {
        val decided = mapFetch(cloud.fetchZone(binding))
        if (decided != null) {
            return decided
        }
        if (hasEstablished) {
            return ZoneGate.Stop(BootstrapResult.ActionRequired)
        }
        return saveAndConfirm(binding)
    }

    private suspend fun saveAndConfirm(binding: AccountBinding): ZoneGate {
        val saved = mapSave(cloud.saveZone(binding))
        if (saved != null) {
            return saved
        }
        return mapConfirm(cloud.fetchZone(binding))
    }

    private fun mapFetch(fetch: ZoneFetchResult): ZoneGate? {
        return when (fetch) {
            is ZoneFetchResult.Found -> ZoneGate.Proceed
            is ZoneFetchResult.Missing -> null
            is ZoneFetchResult.Retryable -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneFetchResult.UnknownOutcome -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneFetchResult.AccountChanged -> ZoneGate.Stop(BootstrapResult.ActionRequired)
        }
    }

    private fun mapSave(save: ZoneSaveResult): ZoneGate? {
        return when (save) {
            is ZoneSaveResult.Created -> null
            is ZoneSaveResult.AlreadyExists -> null
            is ZoneSaveResult.UnknownOutcome -> null
            is ZoneSaveResult.Conflict -> ZoneGate.Stop(BootstrapResult.ActionRequired)
            is ZoneSaveResult.Retryable -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneSaveResult.AccountChanged -> ZoneGate.Stop(BootstrapResult.ActionRequired)
        }
    }

    private fun mapConfirm(confirm: ZoneFetchResult): ZoneGate {
        return when (confirm) {
            is ZoneFetchResult.Found -> ZoneGate.Proceed
            is ZoneFetchResult.Missing -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneFetchResult.Retryable -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneFetchResult.UnknownOutcome -> ZoneGate.Stop(BootstrapResult.Retryable)
            is ZoneFetchResult.AccountChanged -> ZoneGate.Stop(BootstrapResult.ActionRequired)
        }
    }
}
