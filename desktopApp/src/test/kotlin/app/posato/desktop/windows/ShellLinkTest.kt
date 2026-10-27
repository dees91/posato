package app.posato.desktop.windows

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Shortcuts built byte by byte after [MS-SHLLINK], the format the Start menu's `.lnk` files use. */
class ShellLinkTest {
    @Test
    fun `given a shortcut to a local program when parsed then its target is the local path and it has no arguments`() {
        val link = ShellLink.parse(shortcut(localPath = "C:\\Program Files\\Foo\\", suffix = "foo.exe"))

        assertEquals(ShellLink("C:\\Program Files\\Foo\\foo.exe", ""), link)
    }

    @Test
    fun `given an item list and a Unicode path when parsed then the item list is skipped and the Unicode path wins`() {
        val unicodePath = "C:\\Programs\\\u017B\u00F3\u0142w\\\u017C\u00F3\u0142w.exe"
        val link = ShellLink.parse(shortcut(localPath = "C:\\Programs\\Zolw\\zolw.exe", unicodePath = unicodePath, idList = ByteArray(40) { 7 }))

        assertEquals(unicodePath, link?.target)
    }

    @Test
    fun `given a shortcut that passes launch arguments when parsed then they are read after the earlier strings`() {
        val link = ShellLink.parse(
            shortcut(
                localPath = "C:\\Users\\a\\AppData\\Local\\Chromium\\chrome_proxy.exe",
                strings = listOf(NAME to "Mail", WORKING_DIR to "C:\\Users\\a", ARGUMENTS to "--profile-directory=Default --app-id=abc"),
            ),
        )

        assertEquals("--profile-directory=Default --app-id=abc", link?.arguments)
    }

    @Test
    fun `given a network target or an advertised installer shortcut when parsed then it has no local target`() {
        assertNull(ShellLink.parse(shortcut(localPath = null))?.target)
        assertNull(ShellLink.parse(shortcut(localPath = "C:\\Windows\\Installer\\x.exe", extraFlags = HAS_DARWIN_ID))?.target)
    }

    @Test
    fun `given a file that is not a shortcut or is cut short when parsed then it is refused without failing`() {
        val whole = shortcut(localPath = "C:\\Program Files\\Foo\\foo.exe")

        assertNull(ShellLink.parse(ByteArray(0)))
        assertNull(ShellLink.parse("MZ not a shortcut at all, but long enough to have a header of seventy-six bytes".toByteArray()))
        (1 until whole.size).forEach { length -> ShellLink.parse(whole.copyOf(length)) }
        assertNull(ShellLink.parse(whole.copyOf(HEADER_SIZE + 10)))
        val bogusOffset = whole.copyOf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(HEADER_SIZE + 16, 0x7FFF_FFF0) }
        assertNull(ShellLink.parse(bogusOffset)?.target)
    }

    private fun shortcut(
        localPath: String?,
        suffix: String = "",
        unicodePath: String? = null,
        idList: ByteArray? = null,
        strings: List<Pair<Int, String>> = emptyList(),
        extraFlags: Int = 0,
    ): ByteArray {
        var flags = HAS_LINK_INFO or IS_UNICODE or extraFlags
        if (idList != null) flags = flags or HAS_ID_LIST
        strings.forEach { (flag, _) -> flags = flags or flag }
        val out = ByteArrayOutputStream()
        out.write(header(flags))
        if (idList != null) {
            out.write(short(idList.size))
            out.write(idList)
        }
        out.write(linkInfo(localPath, suffix, unicodePath))
        strings.sortedBy { it.first }.forEach { (_, value) ->
            out.write(short(value.length))
            out.write(value.toByteArray(Charsets.UTF_16LE))
        }
        out.write(int(0))
        return out.toByteArray()
    }

    private fun header(flags: Int): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(HEADER_SIZE)
        buffer.put(byteArrayOf(0x01, 0x14, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46))
        buffer.putInt(flags)
        return buffer.array()
    }

    private fun linkInfo(
        localPath: String?,
        suffix: String,
        unicodePath: String?,
    ): ByteArray {
        val headerSize = if (unicodePath != null) 0x24 else 0x1C
        val volume = int(16) + int(3) + int(0) + int(16) // size, drive type, serial, label offset (empty label at end)
        val volumeId = volume + byteArrayOf(0, 0, 0, 0)
        val local = (localPath ?: "").toByteArray(Charsets.US_ASCII) + 0
        val common = suffix.toByteArray(Charsets.US_ASCII) + 0
        val network = int(20) + int(0) + int(20) + int(0) + int(0) + byteArrayOf(0)
        val unicode = unicodePath?.let { it.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0) } ?: ByteArray(0)
        val unicodeSuffix = if (unicodePath != null) byteArrayOf(0, 0) else ByteArray(0)
        val body = ByteArrayOutputStream()
        val volumeOffset = headerSize
        val localOffset = volumeOffset + volumeId.size
        val networkOffset = localOffset + local.size
        val commonOffset = networkOffset + network.size
        val unicodeOffset = commonOffset + common.size
        val unicodeSuffixOffset = unicodeOffset + unicode.size
        val size = unicodeSuffixOffset + unicodeSuffix.size
        body.write(int(size))
        body.write(int(headerSize))
        body.write(int(if (localPath != null) 1 else 2))
        body.write(int(if (localPath != null) volumeOffset else 0))
        body.write(int(if (localPath != null) localOffset else 0))
        body.write(int(networkOffset))
        body.write(int(commonOffset))
        if (unicodePath != null) {
            body.write(int(unicodeOffset))
            body.write(int(unicodeSuffixOffset))
        }
        body.write(volumeId)
        body.write(local)
        body.write(network)
        body.write(common)
        body.write(unicode)
        body.write(unicodeSuffix)
        return body.toByteArray()
    }

    private fun int(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun short(value: Int): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    private companion object {
        const val HEADER_SIZE = 0x4C
        const val HAS_ID_LIST = 0x1
        const val HAS_LINK_INFO = 0x2
        const val NAME = 0x4
        const val WORKING_DIR = 0x10
        const val ARGUMENTS = 0x20
        const val IS_UNICODE = 0x80
        const val HAS_DARWIN_ID = 0x1000
    }
}
