package app.posato.feature.about

internal actual fun applicationVersion(): String? {
    return System.getProperty("jpackage.app-version")
}
