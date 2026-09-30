package app.posato.feature.sync.bootstrap

internal fun interface SyncBackgroundTime {
    fun begin(): AutoCloseable

    companion object {
        val None: SyncBackgroundTime = SyncBackgroundTime { AutoCloseable {} }
    }
}
