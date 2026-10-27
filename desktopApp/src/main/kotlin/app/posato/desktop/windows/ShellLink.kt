package app.posato.desktop.windows

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * What a Windows shortcut (`.lnk`, [MS-SHLLINK]) starts: its local target path, or null for a network, item-list-only,
 * or advertised installer shortcut, and the arguments it passes.
 */
internal data class ShellLink(
    val target: String?,
    val arguments: String,
) {
    companion object {
        private const val HEADER_SIZE = 0x4C
        private const val CLSID_OFFSET = 4
        private const val FLAGS_OFFSET = 20
        private const val HAS_ID_LIST = 0x1
        private const val HAS_LINK_INFO = 0x2
        private const val IS_UNICODE = 0x80
        private const val HAS_DARWIN_ID = 0x1000
        private const val VOLUME_AND_LOCAL_PATH = 0x1
        private const val UNICODE_HEADER_SIZE = 0x24
        private const val UNICODE_OFFSETS = 28
        private const val LOCAL_PATH_OFFSET = 16
        private const val SUFFIX_OFFSET = 24
        private const val INFO_FLAGS_OFFSET = 8
        private const val UNSIGNED_SHORT = 0xFFFF

        /** The strings that precede the arguments: name, relative path, and working directory. */
        private val stringsBeforeArguments = listOf(0x4, 0x8, 0x10)
        private const val HAS_ARGUMENTS = 0x20
        private val linkClsid = byteArrayOf(0x01, 0x14, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x46)

        /** Null when [bytes] is not a well-formed shortcut. */
        fun parse(bytes: ByteArray): ShellLink? {
            if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(CLSID_OFFSET, CLSID_OFFSET + linkClsid.size).contentEquals(linkClsid)) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.getInt(0) != HEADER_SIZE) return null
            val flags = buffer.getInt(FLAGS_OFFSET)
            return try {
                buffer.position(HEADER_SIZE)
                if (flags and HAS_ID_LIST != 0) {
                    val itemListSize = buffer.getShort().toInt() and UNSIGNED_SHORT
                    buffer.position(buffer.position() + itemListSize)
                }
                val target = if (flags and HAS_LINK_INFO != 0) linkInfo(buffer) else null
                val unicode = flags and IS_UNICODE != 0
                stringsBeforeArguments.filter { flags and it != 0 }.forEach { readString(buffer, unicode) }
                val arguments = if (flags and HAS_ARGUMENTS != 0) readString(buffer, unicode) else ""
                ShellLink(target.takeIf { flags and HAS_DARWIN_ID == 0 }, arguments)
            } catch (_: BufferUnderflowException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            }
        }

        /** Reads the LinkInfo block and leaves [buffer] after it; a target that points outside the block is ignored. */
        private fun linkInfo(buffer: ByteBuffer): String? {
            val start = buffer.position()
            val size = buffer.getInt(start)
            val end = start + size
            require(size >= UNICODE_OFFSETS && end <= buffer.limit())
            buffer.position(end)
            if (buffer.getInt(start + INFO_FLAGS_OFFSET) and VOLUME_AND_LOCAL_PATH == 0) return null
            val block = ByteBuffer.wrap(buffer.array(), start, size).slice().order(ByteOrder.LITTLE_ENDIAN)
            val unicode = block.getInt(4) >= UNICODE_HEADER_SIZE
            val path = if (unicode) {
                wide(block, block.getInt(UNICODE_OFFSETS)) + wide(block, block.getInt(UNICODE_OFFSETS + 4))
            } else {
                narrow(block, block.getInt(LOCAL_PATH_OFFSET)) + narrow(block, block.getInt(SUFFIX_OFFSET))
            }
            return path.takeIf { it.isNotEmpty() }
        }

        private fun narrow(
            block: ByteBuffer,
            offset: Int,
        ): String {
            require(offset in 0 until block.limit())
            val end = (offset until block.limit()).firstOrNull { block.get(it) == 0.toByte() } ?: block.limit()
            return String(block.array(), block.arrayOffset() + offset, end - offset, ansi)
        }

        private fun wide(
            block: ByteBuffer,
            offset: Int,
        ): String {
            require(offset in 0 until block.limit())
            val text = StringBuilder()
            var index = offset
            while (index + 1 < block.limit() && block.getChar(index) != '\u0000') {
                text.append(block.getChar(index))
                index += 2
            }
            return text.toString()
        }

        private fun readString(
            buffer: ByteBuffer,
            unicode: Boolean,
        ): String {
            val count = buffer.getShort().toInt() and UNSIGNED_SHORT
            val bytes = ByteArray(if (unicode) count * 2 else count)
            buffer.get(bytes)
            return String(bytes, if (unicode) Charsets.UTF_16LE else ansi)
        }

        /** Shortcuts without Unicode paths use the system's ANSI code page. */
        private val ansi: Charset = runCatching { Charset.forName(System.getProperty("native.encoding")) }.getOrDefault(Charsets.ISO_8859_1)
    }
}
