package app.posato.provisioning.play

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * Synthetic service-account material, generated inside each test run so no fixture can resemble a real key.
 */
object PlayTestKeys {
    fun generate(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun pem(pair: KeyPair): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }

    /** A JSON key in the shape Google Cloud downloads, written owner-only unless [permissions] says otherwise. */
    fun keyFile(
        pair: KeyPair,
        permissions: String = "rw-------",
        email: String = "publisher@posato-test.iam.gserviceaccount.com",
    ): Path {
        val file = Files.createTempFile("service-account", ".json")
        val escaped = pem(pair).replace("\n", "\\n")
        file.writeText(
            """
            {
              "type": "service_account",
              "project_id": "posato-test",
              "private_key_id": "0123456789abcdef0123456789abcdef01234567",
              "private_key": "$escaped",
              "client_email": "$email",
              "token_uri": "https://evil.example.com/token"
            }
            """.trimIndent(),
        )
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions))
        return file
    }
}

/** Records every request that would leave the machine and answers it from [route]. */
class FakePlayTransport(
    private val route: (WireRequest) -> WireResponse,
) : PlayTransport {
    val requests = mutableListOf<WireRequest>()

    override fun send(
        request: WireRequest,
        maxBytes: Int
    ): WireResponse {
        requests += request
        return route(request)
    }

    fun calls(): List<String> = requests.map { request -> "${request.method} ${request.uri.path}" }
}

fun ok(body: String = "{}"): WireResponse = WireResponse(200, body.toByteArray())

fun status(
    code: Int,
    body: String = "{}"
): WireResponse = WireResponse(code, body.toByteArray())

/** A PNG signature and IHDR header with the given size and colour type; the pixels are irrelevant to the checks. */
fun png(
    file: Path,
    width: Int,
    height: Int,
    colorType: Int = 2,
    padding: Int = 0,
) {
    file.parent.createDirectories()
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val header = byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + int(width) + int(height) +
        byteArrayOf(8, colorType.toByte(), 0, 0, 0)
    file.writeBytes(signature + header + ByteArray(padding))
}

private fun int(value: Int): ByteArray = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
