package app.posato.provisioning.asc

import app.posato.provisioning.core.ErrorCode
import app.posato.provisioning.core.ProvisioningException
import app.posato.provisioning.core.Transcript
import app.posato.provisioning.model.UploadHeader
import app.posato.provisioning.model.UploadOperation
import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val VALID_URL = "https://store-uploads.example.apple.com/v1/part?signature=SIGNATURE-VALUE"

class UploadPolicyTest {
    @Test
    fun `accepts a PUT to an apple host over https and keeps only the supplied headers`() {
        val part = UploadPolicy.validate(operation(headers = listOf(UploadHeader("Content-Type", "image/png"))), fileSize = 10)

        assertEquals("store-uploads.example.apple.com", part.uri.host)
        assertEquals(listOf("Content-Type" to "image/png"), part.headers)
        assertEquals(2, part.offset)
        assertEquals(5, part.length)
    }

    @Test
    fun `refuses plain http`() = assertRefused(operation(url = "http://store-uploads.example.apple.com/v1/part"), "https")

    @Test
    fun `refuses a host outside apple dot com`() {
        assertRefused(operation(url = "https://uploads.example.com/v1/part"), "apple.com")
        assertRefused(operation(url = "https://store.apple.com.attacker.example/v1/part"), "apple.com")
        assertRefused(operation(url = "https://notapple.com/v1/part"), "apple.com")
        assertRefused(operation(url = "https://.apple.com/v1/part"), "apple.com")
    }

    @Test
    fun `refuses any method other than PUT`() {
        assertRefused(operation(method = "POST"), "PUT")
        assertRefused(operation(method = "put"), "PUT")
    }

    @Test
    fun `refuses user information and an unexpected port`() {
        assertRefused(operation(url = "https://user:pass@store-uploads.example.apple.com/v1/part"), "user information")
        assertRefused(operation(url = "https://store-uploads.example.apple.com:8443/v1/part"), "port")
    }

    @Test
    fun `refuses a byte range outside the file`() {
        assertRefused(operation(offset = 8, length = 5), "byte range")
        assertRefused(operation(offset = -1, length = 5), "byte range")
        assertRefused(operation(offset = 0, length = 0), "byte range")
    }

    @Test
    fun `refuses headers that change the exchange or smuggle a line break`() {
        assertRefused(operation(headers = listOf(UploadHeader("Connection", "close"))), "Connection")
        assertRefused(operation(headers = listOf(UploadHeader("X-Test", "a\r\nInjected: yes"))), "header")
        assertRefused(operation(headers = listOf(UploadHeader("Content-Length", "99"))), "Content-Length")
    }

    @Test
    fun `lets the client derive a matching Content-Length rather than repeating it`() {
        val part = UploadPolicy.validate(operation(headers = listOf(UploadHeader("Content-Length", "5"))), fileSize = 10)

        assertTrue(part.headers.isEmpty())
    }

    @Test
    fun `sends exactly the slice each operation names`() {
        val transport = CapturingTransport()
        val bytes = "0123456789".toByteArray()

        ScreenshotUploader(transport, Transcript { }).upload(listOf(operation(offset = 0, length = 4), operation(offset = 4, length = 6)), bytes)

        assertContentEquals("0123".toByteArray(), transport.bodies[0])
        assertContentEquals("456789".toByteArray(), transport.bodies[1])
    }

    @Test
    fun `validates every operation before sending any part`() {
        val transport = CapturingTransport()

        assertFailsWith<ProvisioningException> {
            ScreenshotUploader(transport, Transcript { }).upload(
                listOf(operation(offset = 0, length = 4), operation(url = "https://evil.example.net/part", offset = 4, length = 6)),
                "0123456789".toByteArray(),
            )
        }

        assertTrue(transport.bodies.isEmpty())
    }

    @Test
    fun `never records the upload URL in the transcript`() {
        val lines = mutableListOf<String>()

        ScreenshotUploader(CapturingTransport(), Transcript { line -> lines.add(line) }).upload(listOf(operation()), ByteArray(10))

        assertEquals(1, lines.size)
        assertFalse(lines.single().contains("SIGNATURE-VALUE"))
        assertFalse(lines.single().contains("apple.com"))
    }

    @Test
    fun `reports a rejected part and a transport failure as upload failures`() {
        val rejected = assertFailsWith<ProvisioningException> {
            ScreenshotUploader(CapturingTransport(status = 403), Transcript { }).upload(listOf(operation()), ByteArray(10))
        }
        val unreachable = assertFailsWith<ProvisioningException> {
            ScreenshotUploader({ _, _, _ -> throw IOException("reset") }, Transcript { }).upload(listOf(operation()), ByteArray(10))
        }

        assertEquals(ErrorCode.UPLOAD_FAILED, rejected.code)
        assertTrue(rejected.message.orEmpty().contains("HTTP 403"))
        assertEquals(ErrorCode.UPLOAD_FAILED, unreachable.code)
    }

    private fun assertRefused(
        operation: UploadOperation,
        reasonFragment: String
    ) {
        val failure = assertFailsWith<ProvisioningException> { UploadPolicy.validate(operation, fileSize = 10) }
        assertEquals(ErrorCode.UPLOAD_REFUSED, failure.code)
        assertTrue(failure.message.orEmpty().contains(reasonFragment), failure.message)
        assertFalse(failure.message.orEmpty().contains(operation.url.orEmpty()), "The refusal must not repeat the URL.")
    }

    private fun operation(
        method: String = "PUT",
        url: String = VALID_URL,
        offset: Long = 2,
        length: Long = 5,
        headers: List<UploadHeader> = emptyList(),
    ) = UploadOperation(method, url, offset, length, headers)

    private class CapturingTransport(
        private val status: Int = 200
    ) : UploadTransport {
        val bodies = mutableListOf<ByteArray>()

        override fun put(
            part: UploadPart,
            body: ByteArray,
            timeout: Duration,
        ): Int {
            bodies.add(body)
            return status
        }
    }
}
