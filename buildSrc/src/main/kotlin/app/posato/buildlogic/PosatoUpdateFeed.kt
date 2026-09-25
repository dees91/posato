package app.posato.buildlogic

import org.gradle.api.GradleException
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

enum class UpdateChannel(
    val propertyValue: String,
    val feedFileName: String,
) {
    RELEASE("release", "appcast.xml"),
    CANDIDATE("candidate", "appcast-test.xml"),
}

data class UpdateFeedConfiguration(
    val channel: UpdateChannel?,
    val feedUrl: String?,
    val publicKey: String?,
)

data class AppcastExpectation(
    val feedUrl: String,
    val publicKey: String,
    val buildNumber: String,
    val previousBuildNumber: String?,
    val downloadUrl: String,
    val archive: ByteArray,
)

object PosatoUpdateFeed {
    const val STABLE_FEED_URL = "https://github.com/dees91/posato/releases/latest/download/appcast.xml"
    const val STABLE_PUBLIC_KEY = "AFui5Ws+53G4l9RpIjPVwRoKGROvBWt0raf8YtGUgTM="
    const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/dees91/posato/releases/download/"
    const val MINIMUM_SYSTEM_VERSION = "15.0"
    const val HARDWARE_REQUIREMENTS = "arm64"

    private const val SPARKLE_NAMESPACE = "http://www.andymatuschak.org/xml-namespaces/sparkle"
    private const val SIGNATURE_BLOCK = "<!-- sparkle-signatures:"
    private const val ED25519_PUBLIC_KEY_PREFIX = "302a300506032b6570032100"
    private const val ED25519_KEY_BYTES = 32
    private const val ED25519_PRIVATE_KEY_PREFIX = "302e020100300506032b657004220420"
    private const val RFC_SECRET = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
    private const val RFC_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
    private val loopbackFeed = Regex("""http://127\.0\.0\.1:[0-9]{1,5}/[A-Za-z0-9._/-]+""")
    private val httpsFeed = Regex("""https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+""")
    private val buildNumberValue = Regex("""[1-9][0-9]{0,8}""")
    private const val CANDIDATE_FEED_SUFFIX = "/appcast-test.xml"
    private const val STABLE_RELEASES_PREFIX = "https://github.com/dees91/posato/releases/latest/"

    fun resolve(
        channel: String?,
        feedUrl: String?,
        publicKey: String?,
    ): UpdateFeedConfiguration {
        verifyContract()
        return when (channel) {
            null -> development(feedUrl, publicKey)
            UpdateChannel.RELEASE.propertyValue -> release(feedUrl, publicKey)
            UpdateChannel.CANDIDATE.propertyValue -> candidate(feedUrl, publicKey)
            else -> throw GradleException("posatoMacOsUpdateChannel must be release or candidate.")
        }
    }

    fun releaseDownloadPrefix(marketingVersion: String): String = "${RELEASE_DOWNLOAD_PREFIX}v$marketingVersion/"

    /** A candidate's download prefix: an HTTPS or loopback directory URL. */
    fun candidateDownloadPrefix(prefix: String?): String {
        if (prefix == null || !prefix.endsWith("/") || (!httpsFeed.matches(prefix.dropLast(1)) && !loopbackFeed.matches(prefix.dropLast(1)))) {
            throw GradleException("A candidate feed needs -PposatoMacOsUpdateDownloadPrefix=<HTTPS or loopback URL ending in />.")
        }
        return prefix
    }

    fun readsStableFeed(feedUrl: String): Boolean = feedUrl.lowercase().startsWith(STABLE_RELEASES_PREFIX)

    /** Every reason the signed feed may not be published with [expectation]'s archive; empty when it may. */
    fun appcastProblems(
        appcast: ByteArray,
        expectation: AppcastExpectation,
    ): List<String> {
        verifyContract()
        verifyAppcastContract()
        return problemsOf(appcast, expectation)
    }

    private fun problemsOf(
        appcast: ByteArray,
        expectation: AppcastExpectation,
    ): List<String> {
        val problems = mutableListOf<String>()
        if (expectation.feedUrl == STABLE_FEED_URL && expectation.publicKey != STABLE_PUBLIC_KEY) {
            problems += "The application reads the stable feed with a key other than the tracked release key."
        }
        val text = appcast.toString(Charsets.UTF_8)
        val marker = text.lastIndexOf(SIGNATURE_BLOCK)
        if (marker < 0) return problems + "The appcast carries no Sparkle feed signature."
        val signedLength = signatureField(text.substring(marker), "length")?.toIntOrNull()
        val feedSignature = signatureField(text.substring(marker), "edSignature")
        val signedBytes = text.substring(0, marker).toByteArray(Charsets.UTF_8)
        if (signedLength != signedBytes.size || feedSignature == null ||
            !verifies(expectation.publicKey, signedBytes, feedSignature)
        ) {
            problems += "The feed signature does not verify with the application's key."
        }
        val items = parse(signedBytes)?.getElementsByTagName("item")
            ?: return problems + "The appcast is not well-formed XML."
        if (items.length != 1) return problems + "The appcast must contain exactly one item, not ${items.length}."
        return problems + itemProblems(items.item(0) as Element, expectation)
    }

    private fun itemProblems(
        item: Element,
        expectation: AppcastExpectation,
    ): List<String> = buildList {
        listOf("enclosure", "description").filter { item.getElementsByTagName(it).length != 1 }.forEach { add("The item needs exactly one $it.") }
        if (item.getElementsByTagNameNS(SPARKLE_NAMESPACE, "version").length != 1) add("The item needs exactly one sparkle:version.")
        val version = sparkleText(item, "version")
        if (version != expectation.buildNumber) add("sparkle:version $version is not the build number ${expectation.buildNumber}.")
        val previous = expectation.previousBuildNumber
        if (previous != null && !buildNumberValue.matches(previous)) {
            add("The previous build number $previous is not a positive integer.")
        } else if (previous != null && (version?.toLongOrNull() ?: 0L) <= previous.toLong()) {
            add("sparkle:version $version is not above $previous.")
        }
        if (sparkleText(item, "minimumSystemVersion") != MINIMUM_SYSTEM_VERSION) add("sparkle:minimumSystemVersion is not $MINIMUM_SYSTEM_VERSION.")
        if (sparkleText(item, "hardwareRequirements") != HARDWARE_REQUIREMENTS) add("sparkle:hardwareRequirements is not $HARDWARE_REQUIREMENTS.")
        if (sparkleText(item, "releaseNotesLink") != null || sparkleText(item, "fullReleaseNotesLink") != null) {
            add("Release notes must be embedded, not linked.")
        }
        val description = item.getElementsByTagName("description").item(0) as? Element
        if (description == null || description.getAttributeNS(SPARKLE_NAMESPACE, "format") != "plain-text") {
            add("The item needs embedded plain-text release notes.")
        }
        if (item.getElementsByTagNameNS(SPARKLE_NAMESPACE, "deltas").length != 0) add("The item must not offer delta updates.")
        val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element
        if (enclosure == null) {
            add("The item has no enclosure.")
            return@buildList
        }
        if (enclosure.getAttribute("url") != expectation.downloadUrl) add("The enclosure does not point to ${expectation.downloadUrl}.")
        if (enclosure.getAttribute("length") != expectation.archive.size.toString()) add("The enclosure length does not match the archive.")
        val archiveSignature = enclosure.getAttributeNS(SPARKLE_NAMESPACE, "edSignature")
        if (archiveSignature.isEmpty() || !verifies(expectation.publicKey, expectation.archive, archiveSignature)) {
            add("The archive signature does not verify with the application's key.")
        }
    }

    private fun development(
        feedUrl: String?,
        publicKey: String?,
    ): UpdateFeedConfiguration {
        if ((feedUrl == null) != (publicKey == null)) throw GradleException("Set posatoMacOsUpdateFeedUrl and posatoMacOsUpdatePublicKey together.")
        if (feedUrl != null) requireFeedUrl(feedUrl)
        return UpdateFeedConfiguration(null, feedUrl, publicKey)
    }

    private fun release(
        feedUrl: String?,
        publicKey: String?,
    ): UpdateFeedConfiguration {
        if (feedUrl != null && feedUrl != STABLE_FEED_URL) throw GradleException("A release reads only the stable feed $STABLE_FEED_URL.")
        if (publicKey != null && publicKey != STABLE_PUBLIC_KEY) throw GradleException("A release embeds only the tracked release key.")
        return UpdateFeedConfiguration(UpdateChannel.RELEASE, STABLE_FEED_URL, STABLE_PUBLIC_KEY)
    }

    private fun candidate(
        feedUrl: String?,
        publicKey: String?,
    ): UpdateFeedConfiguration {
        if (feedUrl == null || publicKey == null) {
            throw GradleException("A candidate needs posatoMacOsUpdateFeedUrl and posatoMacOsUpdatePublicKey for its test feed.")
        }
        if (readsStableFeed(feedUrl)) throw GradleException("A candidate must never read the stable feed.")
        requireFeedUrl(feedUrl)
        if (!feedUrl.endsWith(CANDIDATE_FEED_SUFFIX)) throw GradleException("A candidate's feed URL must end in $CANDIDATE_FEED_SUFFIX.")
        return UpdateFeedConfiguration(UpdateChannel.CANDIDATE, feedUrl, publicKey)
    }

    private fun requireFeedUrl(feedUrl: String) {
        if (!httpsFeed.matches(feedUrl) && !loopbackFeed.matches(feedUrl)) {
            throw GradleException("posatoMacOsUpdateFeedUrl must use HTTPS or a loopback test server.")
        }
    }

    private fun signatureField(
        block: String,
        name: String,
    ): String? = block.lineSequence().map(String::trim).firstOrNull { it.startsWith("$name:") }?.substringAfter(':')?.trim()

    private fun sparkleText(
        item: Element,
        name: String,
    ): String? = item.getElementsByTagNameNS(SPARKLE_NAMESPACE, name).item(0)?.textContent?.trim()

    private fun parse(xml: ByteArray): org.w3c.dom.Document? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        } catch (_: org.xml.sax.SAXException) {
            null
        }
    }

    private fun verifies(
        publicKey: String,
        data: ByteArray,
        signature: String,
    ): Boolean {
        return try {
            val raw = Base64.getDecoder().decode(publicKey)
            if (raw.size != ED25519_KEY_BYTES) return false
            val encoded = hex(ED25519_PUBLIC_KEY_PREFIX) + raw
            val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(encoded))
            Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(data)
                verify(Base64.getDecoder().decode(signature))
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    /** A synthetic feed signed with the RFC 8032 test 1 secret must pass, and every single defect must be reported. */
    private fun verifyAppcastContract() {
        val signer = KeyFactory.getInstance("Ed25519").generatePrivate(
            java.security.spec.PKCS8EncodedKeySpec(hex(ED25519_PRIVATE_KEY_PREFIX + RFC_SECRET)),
        )
        val sign = { data: ByteArray ->
            Base64.getEncoder().encodeToString(
                Signature.getInstance("Ed25519").run {
                    initSign(signer)
                    update(data)
                    sign()
                },
            )
        }
        val archive = "posato".toByteArray()
        val expectation = AppcastExpectation(
            feedUrl = "https://example.invalid/appcast-test.xml",
            publicKey = Base64.getEncoder().encodeToString(hex(RFC_PUBLIC_KEY)),
            buildNumber = "20",
            previousBuildNumber = "19",
            downloadUrl = "https://example.invalid/Posato.dmg",
            archive = archive,
        )
        val feed = { version: String, extra: String, archiveSignature: String ->
            val body = """<?xml version="1.0" standalone="yes"?><rss xmlns:sparkle="$SPARKLE_NAMESPACE" version="2.0"><channel><item>""" +
                "<sparkle:version>$version</sparkle:version><sparkle:minimumSystemVersion>$MINIMUM_SYSTEM_VERSION</sparkle:minimumSystemVersion>" +
                "<sparkle:hardwareRequirements>$HARDWARE_REQUIREMENTS</sparkle:hardwareRequirements>$extra" +
                """<description sparkle:format="plain-text">Notes</description>""" +
                """<enclosure url="${expectation.downloadUrl}" length="${archive.size}" sparkle:edSignature="$archiveSignature"/>""" +
                "</item></channel></rss>"
            val bytes = body.toByteArray()
            (body + "$SIGNATURE_BLOCK\nedSignature: ${sign(bytes)}\nlength: ${bytes.size}\n-->").toByteArray()
        }
        val goodArchiveSignature = sign(archive)
        val valid = feed("20", "", goodArchiveSignature)
        val tamperedFeed = valid.copyOf().also { it[it.indexOf('N'.code.toByte())] = 'M'.code.toByte() }
        val cases = listOf(
            problemsOf(valid, expectation).isEmpty(),
            problemsOf(tamperedFeed, expectation).isNotEmpty(),
            problemsOf(valid, expectation.copy(archive = "posatO".toByteArray())).isNotEmpty(),
            problemsOf(feed("19", "", goodArchiveSignature), expectation.copy(buildNumber = "19")).isNotEmpty(),
            problemsOf(feed("20", "<sparkle:releaseNotesLink>https://example.invalid</sparkle:releaseNotesLink>", goodArchiveSignature), expectation)
                .isNotEmpty(),
            problemsOf(feed("20", "", sign("other".toByteArray())), expectation).isNotEmpty(),
            problemsOf(valid, expectation.copy(feedUrl = STABLE_FEED_URL)).isNotEmpty(),
            problemsOf(valid, expectation.copy(previousBuildNumber = "1.0.0")).isNotEmpty(),
            readsStableFeed("https://GitHub.com/dees91/Posato/releases/latest/download/appcast-test.xml"),
        )
        if (cases.any { !it }) {
            throw GradleException("The Posato appcast validation failed its regression contract.")
        }
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    /** RFC 8032 section 7.1, test 1: the empty message signed by a published key; guards the verifier and the channel rules. */
    private fun verifyContract() {
        val rfcKey = Base64.getEncoder().encodeToString(hex(RFC_PUBLIC_KEY))
        val rfcSignature = Base64.getEncoder().encodeToString(
            hex(
                "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
            ),
        )
        val tampered = Base64.getEncoder().encodeToString(byteArrayOf(1))
        val verifierHolds = verifies(rfcKey, ByteArray(0), rfcSignature) &&
            !verifies(rfcKey, byteArrayOf(0), rfcSignature) &&
            !verifies(STABLE_PUBLIC_KEY, ByteArray(0), rfcSignature) &&
            !verifies(tampered, ByteArray(0), rfcSignature)
        val feedRulesHold = loopbackFeed.matches("http://127.0.0.1:8765/appcast.xml") &&
            httpsFeed.matches(STABLE_FEED_URL) &&
            !httpsFeed.matches("http://example.com/appcast.xml") &&
            !loopbackFeed.matches("http://127.0.0.1.example.com/appcast.xml") &&
            Base64.getDecoder().decode(STABLE_PUBLIC_KEY).size == ED25519_KEY_BYTES
        if (!verifierHolds || !feedRulesHold) {
            throw GradleException("The Posato update feed rules failed their regression contract.")
        }
    }
}
