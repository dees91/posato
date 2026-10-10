package app.posato.feature.sync.folder

import app.posato.feature.sync.bootstrap.AccountBinding
import app.posato.feature.sync.bootstrap.BindingResolution
import app.posato.feature.sync.bootstrap.BootstrapEncoding
import app.posato.feature.sync.bootstrap.KeyAccount
import app.posato.feature.sync.bootstrap.KeyItemReadResult
import app.posato.feature.sync.bootstrap.WorkspaceAnchor
import app.posato.feature.sync.data.JdkSyncCryptoProvider
import app.posato.feature.sync.domain.KeyEpochId
import app.posato.feature.sync.domain.TransportEpochId
import app.posato.feature.sync.domain.WorkspaceId
import app.posato.feature.sync.testIdentifier
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FolderPairingTest {
    private val crypto = JdkSyncCryptoProvider()
    private val anchor = WorkspaceAnchor(WorkspaceId(testIdentifier(1)), TransportEpochId(testIdentifier(2)), KeyEpochId(testIdentifier(3)))
    private val account = checkNotNull(KeyAccount.fromText(BootstrapEncoding.identifierToAccountText(anchor.workspaceId.value)))
    private var now = 1_000_000L

    private suspend fun FolderSyncPorts.binding(): AccountBinding {
        return assertIs<BindingResolution.Available>(resolveBinding()).binding
    }

    private suspend fun member(root: Path): FolderSyncPorts {
        val ports = FolderSyncPorts({ root }, Files.createTempDirectory("posato-member"), crypto)
        val binding = ports.binding()
        ports.saveZone(binding)
        ports.createAnchor(binding, anchor)
        val item = checkNotNull(
            BootstrapEncoding.encodeKeyItem(anchor.workspaceId, anchor.transportEpochId, anchor.keyEpochId, ByteArray(32) { 7 }),
        )
        ports.createItem(binding, account, item)
        return ports
    }

    private fun joiner(root: Path): FolderSyncPorts {
        return FolderSyncPorts({ root }, Files.createTempDirectory("posato-joiner"), crypto)
    }

    private fun pairing(ports: FolderSyncPorts): FolderPairing {
        return FolderPairing(ports, crypto) { now }
    }

    @Test
    fun `given a fresh offer when the joiner enters the code then it holds the member's workspace key and the offer is gone`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val member = member(root)
        val joiner = joiner(root)
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member).offer()).offer

        assertEquals(PairingAcceptResult.JOINED, pairing(joiner).accept(offer.code.lowercase().chunked(4).joinToString(" ")))

        val received = assertIs<KeyItemReadResult.Found>(joiner.readItem(joiner.binding(), account)).value
        val expected = assertIs<KeyItemReadResult.Found>(member.readItem(member.binding(), account)).value
        assertContentEquals(expected.copyBytes(), received.copyBytes())
        assertEquals(emptyList(), Files.list(root.resolve("Posato").resolve("pairing")).use { it.toList() })
        assertEquals(PairingAcceptResult.NOT_FOUND, pairing(joiner(root)).accept(offer.code))
    }

    @Test
    fun `given a well formed code whose offer has not arrived when entered then the joiner waits for the folder`() = runTest {
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member(Files.createTempDirectory("posato-folder"))).offer()).offer
        val elsewhere = Files.createTempDirectory("posato-folder")
        val joiner = joiner(elsewhere)
        joiner.saveZone(joiner.binding())
        joiner.createAnchor(joiner.binding(), anchor)

        assertEquals(PairingAcceptResult.NOT_FOUND, pairing(joiner).accept(offer.code))
    }

    @Test
    fun `given a code with one mistyped character when entered then it is reported as mistyped`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member(root)).offer()).offer
        val mistyped = offer.code.replaceRange(3, 4, if (offer.code[3] == 'A') "B" else "A")

        assertEquals(PairingAcceptResult.INVALID_CODE, pairing(joiner(root)).accept(mistyped))
    }

    @Test
    fun `given an offer when a wrong code is entered then nothing is joined and the offer stays`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val member = member(root)
        val joiner = joiner(root)
        assertIs<PairingOfferResult.Offered>(pairing(member).offer())

        assertEquals(PairingAcceptResult.INVALID_CODE, pairing(joiner).accept("AAAAAAAAAAAAAAAAAAAAAAAAAA"))
        assertEquals(PairingAcceptResult.INVALID_CODE, pairing(joiner).accept("not a code"))
        assertIs<KeyItemReadResult.Missing>(joiner.readItem(joiner.binding(), account))
        assertEquals(1, Files.list(root.resolve("Posato").resolve("pairing")).use { it.count() })
    }

    @Test
    fun `given an offer older than ten minutes when the code is entered then it is refused`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member(root)).offer()).offer
        now += 10 * 60_000L + 1

        assertEquals(PairingAcceptResult.EXPIRED, pairing(joiner(root)).accept(offer.code))
    }

    @Test
    fun `given a tampered offer file when the code is entered then it is refused`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member(root)).offer()).offer
        val file = Files.list(root.resolve("Posato").resolve("pairing")).use { it.toList().single() }
        val bytes = Files.readAllBytes(file)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        Files.write(file, bytes)

        assertEquals(PairingAcceptResult.REFUSED, pairing(joiner(root)).accept(offer.code))
    }

    @Test
    fun `given an offer for another workspace when the joiner's folder names a different anchor then it is refused`() = runTest {
        val root = Files.createTempDirectory("posato-folder")
        val offer = assertIs<PairingOfferResult.Offered>(pairing(member(root)).offer()).offer
        val otherRoot = Files.createTempDirectory("posato-folder")
        val other = joiner(otherRoot)
        other.saveZone(other.binding())
        other.createAnchor(other.binding(), WorkspaceAnchor(WorkspaceId(testIdentifier(9)), TransportEpochId(testIdentifier(8)), KeyEpochId(testIdentifier(7))))
        Files.createDirectories(otherRoot.resolve("Posato").resolve("pairing"))
        Files.list(root.resolve("Posato").resolve("pairing")).use { files ->
            files.forEach { Files.copy(it, otherRoot.resolve("Posato").resolve("pairing").resolve(it.fileName)) }
        }

        assertEquals(PairingAcceptResult.WRONG_WORKSPACE, pairing(other).accept(offer.code))
    }
}
