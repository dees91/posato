package app.posato.desktop

import java.io.File

internal object TranslationGuard {
    private const val TITLE = "This version is for Intel Macs"
    private const val MESSAGE = "This Mac has Apple silicon. Download Posato for Apple silicon."
    private const val DOWNLOAD = "Download"
    private const val QUIT = "Quit"
    private const val DOWNLOAD_URL = "https://github.com/dees91/posato/releases/latest"

    fun permitsLaunch(): Boolean {
        if (!MacTranslationNative.runsTranslated()) return true
        if (MacTranslationNative.allowsTranslation()) {
            System.err.println("Posato runs under Rosetta translation in a verification build.")
            return true
        }
        MacTranslationNative.refuseTranslation(TITLE, MESSAGE, DOWNLOAD, QUIT, DOWNLOAD_URL)
        return false
    }
}

internal object MacTranslationNative {
    init {
        val resourcesDirectory = checkNotNull(System.getProperty("compose.application.resources.dir"))
        System.load(File(resourcesDirectory, "native/libPosatoWindow.dylib").absolutePath)
    }

    @JvmStatic
    external fun runsTranslated(): Boolean

    @JvmStatic
    external fun allowsTranslation(): Boolean

    @JvmStatic
    external fun refuseTranslation(
        title: String,
        message: String,
        download: String,
        quit: String,
        downloadUrl: String,
    )
}
