package app.posato.desktop.update

import app.posato.feature.update.UpdaterCopy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MacUpdaterBridgeTest {
    @Test
    fun `given the native copy indices when Kotlin orders the copy then every string lands at its native index`() {
        val copy = UpdaterCopy(
            checkForUpdates = "checkForUpdates",
            preparing = "preparing",
            refusedTitle = "refusedTitle",
            refusedSession = "refusedSession",
            refusedOtherInstance = "refusedOtherInstance",
            refusedOther = "refusedOther",
            consentTitle = "consentTitle",
            consentMessage = "consentMessage",
            consentAllow = "consentAllow",
            consentDeny = "consentDeny",
        )

        assertEquals(nativeCopyIndices(), copy.nativeOrder().toList())
    }

    @Test
    fun `given the native refusal constants when mapping refusals then a session and a second instance each get their own message`() {
        assertEquals(nativeConstant("PosatoRefusalSessionActive"), refusalCode(AdmissionRefusal.SESSION_ACTIVE))
        assertEquals(nativeConstant("PosatoRefusalOtherInstance"), refusalCode(AdmissionRefusal.OTHER_INSTANCE))
        val specific = setOf(nativeConstant("PosatoRefusalSessionActive"), nativeConstant("PosatoRefusalOtherInstance"))
        listOf(null, AdmissionRefusal.CLEANUP_UNCERTAIN, AdmissionRefusal.FOREIGN_LEASE, AdmissionRefusal.STORAGE).forEach { refusal ->
            assertFalse(refusalCode(refusal) in specific)
        }
    }

    private fun nativeConstant(name: String): Int {
        val match = checkNotNull(Regex("""static const jint $name = (\d+);""").find(updaterSource()))
        return match.groupValues[1].toInt()
    }

    private fun nativeCopyIndices(): List<String> {
        val body = updaterSource().substringAfter("typedef NS_ENUM(NSUInteger, PosatoCopyIndex) {").substringBefore("};")
        return body.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "PosatoCopyCount" }
            .map { it.removePrefix("PosatoCopy").replaceFirstChar(Char::lowercaseChar) }
    }

    private fun updaterSource(): String = File("src/main/objc/Updater.m").readText()
}
