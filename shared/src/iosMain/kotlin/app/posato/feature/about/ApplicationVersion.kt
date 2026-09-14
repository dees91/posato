package app.posato.feature.about

import platform.Foundation.NSBundle

internal actual fun applicationVersion(): String? {
    return NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}
