package app.posato.control.vm

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class VncClientTest {
    private val challenge = ByteArray(16) { it.toByte() }

    @Test
    fun `the authentication response matches an independent VNC implementation`() {
        // Expected values computed with vncdotool's des_encrypt(_vnc_des(password), bytes(range(16))).
        assertEquals("de678480329e89177a0ed098aae745c9", vncAuthResponse("test-pass-word", challenge).toHex())
        assertEquals("9c22b4f2088c3465a1562c4b9d6edb04", vncAuthResponse("abc", challenge).toHex())
    }

    @Test
    fun `typing wraps uppercase letters and shifted symbols in an explicit shift`() {
        val events = keyEventsForText("Ab!")

        assertEquals(
            listOf(
                SHIFT to true,
                'a'.code to true,
                'a'.code to false,
                SHIFT to false,
                'b'.code to true,
                'b'.code to false,
                SHIFT to true,
                '1'.code to true,
                '1'.code to false,
                SHIFT to false,
            ),
            events,
        )
    }

    @Test
    fun `a command chord presses Alt_L, which the Virtualization server maps to Command`() {
        assertEquals(listOf(ALT to true, 'q'.code to true, 'q'.code to false, ALT to false), keyEventsForChord("cmd-q"))
    }

    @Test
    fun `the client authenticates, captures the framebuffer, and sends keys and clicks`() {
        val server = FakeRfbServer(password = "test-pass-word", challenge = challenge)
        VncClient.connect("127.0.0.1", server.port, "test-pass-word").use { client ->
            val image = client.capture()
            client.type("A")
            client.click(10, 20)

            assertEquals(2, image.width)
            assertEquals(1, image.height)
            assertEquals(0xFF0000, image.getRGB(0, 0) and 0xFFFFFF)
            assertEquals(0x0000FF, image.getRGB(1, 0) and 0xFFFFFF)
        }
        server.join()

        assertEquals(true, server.authenticated)
        assertEquals(
            listOf("key $SHIFT down", "key ${'a'.code} down", "key ${'a'.code} up", "key $SHIFT up"),
            server.events.filter {
                it.startsWith("key")
            },
        )
        assertEquals(listOf("pointer 0 10 20", "pointer 1 10 20", "pointer 0 10 20"), server.events.filter { it.startsWith("pointer") })
    }

    @Test
    fun `a desktop size announcement resizes the next capture and last rect ends an update`() {
        val server = FakeRfbServer(password = "test-pass-word", challenge = challenge, announceResize = true)
        VncClient.connect("127.0.0.1", server.port, "test-pass-word").use { client ->
            val image = client.capture()

            assertEquals(3, client.width)
            assertEquals(3, image.width)
        }
        server.join()

        assertEquals(listOf(0, -223, -224, -258), server.encodings)
    }

    @Test
    fun `a wrong password is refused`() {
        val server = FakeRfbServer(password = "right", challenge = challenge)
        val failure = runCatching { VncClient.connect("127.0.0.1", server.port, "wrong") }.exceptionOrNull()
        server.join()

        assertEquals(false, server.authenticated)
        assertEquals(true, failure != null)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val SHIFT = 0xffe1
        const val ALT = 0xffe9
    }
}

/** A single-connection RFB 3.8 server with VNC authentication and a 2x1 raw framebuffer (red, blue). */
private class FakeRfbServer(
    private val password: String,
    private val challenge: ByteArray,
    private val announceResize: Boolean = false,
) {
    val encodings = CopyOnWriteArrayList<Int>()
    private var resized = false
    private val socket = ServerSocket(0)
    val port: Int = socket.localPort
    val events = CopyOnWriteArrayList<String>()

    @Volatile
    var authenticated = false
    private val worker = thread { serve() }

    fun join() = worker.join(5_000)

    private fun serve() {
        socket.use { server ->
            server.accept().use { connection ->
                val input = DataInputStream(connection.getInputStream())
                val output = DataOutputStream(connection.getOutputStream())
                output.write("RFB 003.008\n".toByteArray())
                input.readFully(ByteArray(12))
                output.write(byteArrayOf(1, 2))
                input.readUnsignedByte()
                output.write(challenge)
                val response = ByteArray(16).also { input.readFully(it) }
                authenticated = response.contentEquals(vncAuthResponse(password, challenge))
                output.writeInt(if (authenticated) 0 else 1)
                if (!authenticated) {
                    output.writeInt(0)
                    return
                }
                input.readUnsignedByte()
                output.writeShort(2)
                output.writeShort(1)
                output.write(ByteArray(16))
                output.writeInt(4)
                output.write("fake".toByteArray())
                loop(input, output)
            }
        }
    }

    private fun loop(
        input: DataInputStream,
        output: DataOutputStream
    ) {
        while (true) {
            when (val type = runCatching { input.readUnsignedByte() }.getOrElse { return }) {
                0 -> {
                    input.readFully(ByteArray(19))
                }

                2 -> {
                    input.readUnsignedByte()
                    repeat(input.readUnsignedShort()) { encodings.add(input.readInt()) }
                }

                3 -> {
                    input.readFully(ByteArray(9))
                    if (announceResize && !resized) {
                        resized = true
                        writeResize(output)
                    } else {
                        writeFrame(output, width = if (resized) 3 else 2)
                    }
                }

                4 -> {
                    val down = input.readUnsignedByte() == 1
                    input.readUnsignedShort()
                    events.add("key ${input.readInt()} ${if (down) "down" else "up"}")
                }

                5 -> {
                    events.add("pointer ${input.readUnsignedByte()} ${input.readUnsignedShort()} ${input.readUnsignedShort()}")
                }

                else -> {
                    error("unexpected client message $type")
                }
            }
        }
    }
}

/** One update announcing a 3x1 desktop, closed by a LastRect marker. */
private fun writeResize(output: DataOutputStream) {
    output.writeByte(0)
    output.writeByte(0)
    output.writeShort(2)
    listOf(0, 0, 3, 1).forEach { output.writeShort(it) }
    output.writeInt(-223)
    listOf(0, 0, 0, 0).forEach { output.writeShort(it) }
    output.writeInt(-224)
}

/** One raw rectangle: red, blue, then black for any further column. */
private fun writeFrame(
    output: DataOutputStream,
    width: Int
) {
    output.writeByte(0)
    output.writeByte(0)
    output.writeShort(1)
    listOf(0, 0, width, 1).forEach { output.writeShort(it) }
    output.writeInt(0)
    output.write(byteArrayOf(0, 0, 0xFF.toByte(), 0, 0xFF.toByte(), 0, 0, 0))
    repeat(width - 2) { output.write(ByteArray(4)) }
}
