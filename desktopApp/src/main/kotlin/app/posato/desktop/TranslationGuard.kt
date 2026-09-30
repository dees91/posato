package app.posato.desktop

internal object TranslationGuard {
    private const val TITLE = "This version is for Intel Macs"
    private const val MESSAGE = "This Mac has Apple silicon. Download Posato for Apple silicon."
    private const val DOWNLOAD = "Download"
    private const val QUIT = "Quit"
    private const val DOWNLOAD_URL = "https://github.com/dees91/posato/releases/latest"

    fun permitsLaunch(): Boolean {
        if (!MacPresenceNative.runsTranslated()) return true
        if (MacPresenceNative.allowsTranslation()) {
            System.err.println("Posato runs under Rosetta translation in a verification build.")
            return true
        }
        MacPresenceNative.refuseTranslation(TITLE, MESSAGE, DOWNLOAD, QUIT, DOWNLOAD_URL)
        return false
    }
}
