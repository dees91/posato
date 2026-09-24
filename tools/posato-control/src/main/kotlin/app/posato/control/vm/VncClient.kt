package app.posato.control.vm

import app.posato.control.core.ControlException
import app.posato.control.core.ErrorCode
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * A minimal RFB 3.8 client for the VNC server that Apple's Virtualization framework runs for a Tart guest.
 *
 * It supports only what verification needs: VNC authentication (security type 2), raw 32-bit framebuffer
 * captures, and key and pointer events. Input over this server reaches the guest as hardware input, so it can
 * answer SecurityAgent, System Settings, and Gatekeeper dialogs that synthetic events inside the guest cannot.
 */
class VncClient private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    width: Int,
    height: Int,
) : AutoCloseable {
    var width: Int = width
        private set
    var height: Int = height
        private set

    /** Requests a full framebuffer update and returns it as an RGB image. */
    fun capture(): BufferedImage {
        output.writeByte(MESSAGE_FRAMEBUFFER_UPDATE_REQUEST)
        output.writeByte(0)
        output.writeShort(0)
        output.writeShort(0)
        output.writeShort(width)
        output.writeShort(height)
        output.flush()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        while (true) {
            val type = input.readUnsignedByte()
            if (type == MESSAGE_FRAMEBUFFER_UPDATE) break
            skipServerMessage(type)
        }
        input.readUnsignedByte()
        val resized = readRectangles(image, input.readUnsignedShort())
        return if (resized) capture() else image
    }

    /** Reads an update's rectangles; returns true when the server announced a new desktop size. */
    private fun readRectangles(
        image: BufferedImage,
        count: Int
    ): Boolean {
        var resized = false
        for (index in 0 until count) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            when (val encoding = input.readInt()) {
                ENCODING_RAW -> {
                    readPixels(image, x, y, w, h)
                }

                ENCODING_DESKTOP_SIZE -> {
                    width = w
                    height = h
                    resized = true
                }

                ENCODING_LAST_RECT -> {
                    return resized
                }

                ENCODING_QEMU_EXTENDED_KEY -> {
                    continue
                }

                else -> {
                    throw ControlException(ErrorCode.COMMAND_FAILED, "The VNC server sent unsupported encoding $encoding.")
                }
            }
        }
        return resized
    }

    fun key(
        keysym: Int,
        down: Boolean
    ) {
        output.writeByte(MESSAGE_KEY_EVENT)
        output.writeByte(if (down) 1 else 0)
        output.writeShort(0)
        output.writeInt(keysym)
        output.flush()
    }

    fun pointer(
        x: Int,
        y: Int,
        buttons: Int
    ) {
        output.writeByte(MESSAGE_POINTER_EVENT)
        output.writeByte(buttons)
        output.writeShort(x)
        output.writeShort(y)
        output.flush()
    }

    /** Moves to (x, y) in framebuffer pixels and clicks the primary button. */
    fun click(
        x: Int,
        y: Int
    ) {
        pointer(x, y, 0)
        pause(POINTER_SETTLE_MS)
        pointer(x, y, 1)
        pointer(x, y, 0)
    }

    fun type(text: String) = send(keyEventsForText(text))

    /** Presses a named key or chord such as `return`, `tab`, or `cmd-q`. */
    fun press(chord: String) = send(keyEventsForChord(chord))

    private fun send(events: List<Pair<Int, Boolean>>) {
        events.forEach { (keysym, down) ->
            key(keysym, down)
            pause(KEY_SETTLE_MS)
        }
    }

    private fun readPixels(
        image: BufferedImage,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        val row = ByteArray(w * BYTES_PER_PIXEL)
        for (line in 0 until h) {
            input.readFully(row)
            for (column in 0 until w) {
                val offset = column * BYTES_PER_PIXEL
                val blue = row[offset].toInt() and BYTE_MASK
                val green = row[offset + 1].toInt() and BYTE_MASK
                val red = row[offset + 2].toInt() and BYTE_MASK
                image.setRGB(x + column, y + line, (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue)
            }
        }
    }

    private fun skipServerMessage(type: Int) {
        when (type) {
            MESSAGE_BELL -> {
                return
            }

            MESSAGE_CUT_TEXT -> {
                input.readFully(ByteArray(CUT_TEXT_PADDING))
                input.readFully(ByteArray(input.readInt()))
            }

            else -> {
                throw ControlException(ErrorCode.COMMAND_FAILED, "The VNC server sent unexpected message $type.")
            }
        }
    }

    override fun close() = socket.close()

    companion object {
        fun connect(
            host: String,
            port: Int,
            password: String,
            timeoutMs: Int = CONNECT_TIMEOUT_MS
        ): VncClient {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = READ_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            try {
                handshake(input, output, password)
                output.writeByte(1)
                output.flush()
                val width = input.readUnsignedShort()
                val height = input.readUnsignedShort()
                input.readFully(ByteArray(PIXEL_FORMAT_LENGTH))
                input.readFully(ByteArray(input.readInt()))
                setPixelFormat(output)
                setRawEncoding(output)
                return VncClient(socket, input, output, width, height)
            } catch (exception: ControlException) {
                socket.close()
                throw exception
            }
        }

        private fun handshake(
            input: DataInputStream,
            output: DataOutputStream,
            password: String
        ) {
            input.readFully(ByteArray(PROTOCOL_VERSION.length))
            output.write(PROTOCOL_VERSION.toByteArray())
            val types = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            if (SECURITY_VNC_AUTH.toByte() !in types) {
                throw ControlException(ErrorCode.COMMAND_FAILED, "The VNC server does not offer VNC authentication.")
            }
            output.writeByte(SECURITY_VNC_AUTH)
            val challenge = ByteArray(CHALLENGE_LENGTH).also { input.readFully(it) }
            output.write(vncAuthResponse(password, challenge))
            output.flush()
            if (input.readInt() != 0) throw ControlException(ErrorCode.COMMAND_FAILED, "The VNC server rejected the password.")
        }

        /** 32 bits per pixel, depth 24, little-endian true colour with red at bit 16, green at 8, blue at 0. */
        private fun setPixelFormat(output: DataOutputStream) {
            output.writeByte(MESSAGE_SET_PIXEL_FORMAT)
            output.write(ByteArray(PIXEL_FORMAT_PADDING))
            output.write(byteArrayOf(BITS_PER_PIXEL.toByte(), DEPTH.toByte(), 0, 1))
            output.writeShort(BYTE_MASK)
            output.writeShort(BYTE_MASK)
            output.writeShort(BYTE_MASK)
            output.write(byteArrayOf(RED_SHIFT.toByte(), GREEN_SHIFT.toByte(), 0))
            output.write(ByteArray(PIXEL_FORMAT_PADDING))
        }

        /**
         * Raw pixels plus the pseudo-encodings the Virtualization server expects: without DesktopSize its VNC server
         * hits an internal assertion and stops the whole VM ("unclear if we can support clients that don't support
         * this pseudo encoding").
         */
        private fun setRawEncoding(output: DataOutputStream) {
            val encodings = listOf(ENCODING_RAW, ENCODING_DESKTOP_SIZE, ENCODING_LAST_RECT, ENCODING_QEMU_EXTENDED_KEY)
            output.writeByte(MESSAGE_SET_ENCODINGS)
            output.writeByte(0)
            output.writeShort(encodings.size)
            encodings.forEach { output.writeInt(it) }
            output.flush()
        }

        private fun pause(milliseconds: Long) = Thread.sleep(milliseconds)
    }
}

/** VNC authentication: DES-encrypt the challenge with the first eight password bytes, each bit-reversed (RFC 6143 7.2.2). */
internal fun vncAuthResponse(
    password: String,
    challenge: ByteArray
): ByteArray {
    val key = ByteArray(DES_KEY_LENGTH)
    password.toByteArray().take(DES_KEY_LENGTH).forEachIndexed { index, byte -> key[index] = reverseBits(byte) }
    val cipher = Cipher.getInstance("DES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
    return cipher.doFinal(challenge)
}

private fun reverseBits(byte: Byte): Byte = (Integer.reverse(byte.toInt() and BYTE_MASK) ushr REVERSE_SHIFT).toByte()

/**
 * Key events that type `text` on a US layout. The Virtualization server needs an explicit Shift press around the
 * unshifted key for uppercase letters and shifted symbols; sending the shifted keysym alone types the lowercase key.
 */
internal fun keyEventsForText(text: String): List<Pair<Int, Boolean>> = text.flatMap { character ->
    val base = when {
        character.isUpperCase() -> character.lowercaseChar()
        else -> SHIFTED_SYMBOLS[character]
    }
    when {
        base != null -> listOf(KEYSYM_SHIFT to true, base.code to true, base.code to false, KEYSYM_SHIFT to false)
        character == ' ' -> listOf(KEYSYM_SPACE to true, KEYSYM_SPACE to false)
        else -> listOf(character.code to true, character.code to false)
    }
}

/** Key events for a chord such as `cmd-q`, `shift-tab`, or a single named key such as `return`. */
internal fun keyEventsForChord(chord: String): List<Pair<Int, Boolean>> {
    val parts = chord.split("-").map { part ->
        NAMED_KEYS[part.lowercase()] ?: part.singleOrNull()?.code
            ?: throw ControlException(
                ErrorCode.USAGE,
                "Unknown key '$part' in '$chord'.",
                "Use ${NAMED_KEYS.keys.sorted().joinToString(", ")} or one character.",
            )
    }
    return parts.map { it to true } + parts.reversed().map { it to false }
}

private const val PROTOCOL_VERSION = "RFB 003.008\n"
private const val SECURITY_VNC_AUTH = 2
private const val CHALLENGE_LENGTH = 16
private const val DES_KEY_LENGTH = 8
private const val REVERSE_SHIFT = 24
private const val BYTE_MASK = 0xFF
private const val PIXEL_FORMAT_LENGTH = 16
private const val PIXEL_FORMAT_PADDING = 3
private const val CUT_TEXT_PADDING = 3
private const val BITS_PER_PIXEL = 32
private const val DEPTH = 24
private const val BYTES_PER_PIXEL = 4
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ENCODING_RAW = 0
private const val ENCODING_DESKTOP_SIZE = -223
private const val ENCODING_LAST_RECT = -224
private const val ENCODING_QEMU_EXTENDED_KEY = -258
private const val MESSAGE_SET_PIXEL_FORMAT = 0
private const val MESSAGE_SET_ENCODINGS = 2
private const val MESSAGE_FRAMEBUFFER_UPDATE_REQUEST = 3
private const val MESSAGE_KEY_EVENT = 4
private const val MESSAGE_POINTER_EVENT = 5
private const val MESSAGE_FRAMEBUFFER_UPDATE = 0
private const val MESSAGE_BELL = 2
private const val MESSAGE_CUT_TEXT = 3
private const val CONNECT_TIMEOUT_MS = 5_000
private const val READ_TIMEOUT_MS = 30_000
private const val KEY_SETTLE_MS = 30L
private const val POINTER_SETTLE_MS = 150L
private const val KEYSYM_SHIFT = 0xffe1
private const val KEYSYM_SPACE = 0x20

/** Command is Alt_L on the Virtualization VNC server; Option is Meta_L. */
private val NAMED_KEYS: Map<String, Int> = mapOf(
    "return" to 0xff0d,
    "enter" to 0xff0d,
    "tab" to 0xff09,
    "escape" to 0xff1b,
    "esc" to 0xff1b,
    "backspace" to 0xff08,
    "delete" to 0xffff,
    "space" to KEYSYM_SPACE,
    "left" to 0xff51,
    "up" to 0xff52,
    "right" to 0xff53,
    "down" to 0xff54,
    "shift" to KEYSYM_SHIFT,
    "ctrl" to 0xffe3,
    "cmd" to 0xffe9,
    "command" to 0xffe9,
    "opt" to 0xffe7,
    "option" to 0xffe7,
)

private val SHIFTED_SYMBOLS: Map<Char, Char> = mapOf(
    '~' to '`',
    '!' to '1',
    '@' to '2',
    '#' to '3',
    '$' to '4',
    '%' to '5',
    '^' to '6',
    '&' to '7',
    '*' to '8',
    '(' to '9',
    ')' to '0',
    '_' to '-',
    '+' to '=',
    '{' to '[',
    '}' to ']',
    '|' to '\\',
    ':' to ';',
    '"' to '\'',
    '<' to ',',
    '>' to '.',
    '?' to '/',
)
