package app.posato.buildlogic

import java.io.File

/**
 * The verification-only seams of the ADR 0007 amendment of 2026-10-09: one Gradle property compiles them
 * into the synchronization companion and marks the development package; release packaging and the DMG
 * check refuse both marks.
 */
object PosatoVerificationSeams {
    const val PROPERTY = "posatoMacOsVerificationSeams"
    const val INFO_PLIST_KEY = "PosatoVerificationSeams"
    const val MARKER = "posato-verification-seams-v1"
    val SWIFT_FLAGS = listOf("-Xswiftc", "-DPOSATO_VERIFICATION")

    fun enabled(value: String?): Boolean = value == "true"

    /** What an application bundle carries: the Info.plist key and the companion marker, read from bytes. */
    data class Found(
        val infoPlistKey: Boolean,
        val companionMarker: Boolean,
        val companionPresent: Boolean,
    ) {
        val any: Boolean get() = infoPlistKey || companionMarker
    }

    fun scan(application: File): Found {
        val info = application.resolve("Contents/Info.plist")
        val companion = application.resolve("Contents/Helpers/PosatoMacOSSync.app/Contents/MacOS/PosatoMacOSSync")
        return Found(
            infoPlistKey = info.isFile && contains(info.readBytes(), INFO_PLIST_KEY.toByteArray()),
            companionMarker = companion.isFile && contains(companion.readBytes(), MARKER.toByteArray()),
            companionPresent = companion.isFile,
        )
    }

    /** The refusal every packaging check reports, so a control run can assert it. */
    fun mismatch(
        found: Found,
        expected: Boolean,
    ): String? {
        // Absence of the marker proves nothing when there is no companion to read.
        return when {
            expected && !(found.infoPlistKey && found.companionMarker) -> "The verification package lacks its verification seams."
            !expected && found.any -> "The package carries the verification-only seams and cannot be released."
            !expected && !found.companionPresent -> "The package has no synchronization companion to check for verification seams."
            else -> null
        }
    }

    private fun contains(
        haystack: ByteArray,
        needle: ByteArray,
    ): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (start in 0..haystack.size - needle.size) {
            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) continue@outer
            }
            return true
        }
        return false
    }
}
