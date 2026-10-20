package app.posato

import app.posato.feature.sync.folder.PairingLinks

/**
 * Hands a link the operating system opened Posato with to the application: a `posato://join` link from a scanned QR
 * code or a message fills the pairing code. Returns whether Posato understood the link.
 */
fun openPosatoLink(url: String): Boolean {
    return PairingLinks.open(url)
}
