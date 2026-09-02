import CryptoKit
import PosatoShared
import XCTest
@testable import Posato

final class CryptoKitSyncProviderTests: XCTestCase {
    func testJcaGoldenFormatOneBundleDecodesAcrossTheKotlinCryptoKitBoundary() {
        XCTAssertTrue(IosFormatOneCryptoVerifier(cryptoProvider: CryptoKitSyncProvider()).verify())
    }

    func testEd25519RFC8032FixtureCrossVerifiesRawJcaAndCryptoKitEncoding() throws {
        let seed = Data(hex: "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        let expectedPublicKey = Data(hex: "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        let expectedSignature = Data(
            hex: "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155" +
                "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        )
        let privateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: seed)
        let signature = try privateKey.signature(for: Data())

        XCTAssertEqual(privateKey.publicKey.rawRepresentation, expectedPublicKey)
        let provider = CryptoKitSyncProvider()
        XCTAssertTrue(provider.verifyEd25519(publicKey: expectedPublicKey, message: Data(), signature: signature))
        XCTAssertTrue(
            provider.verifyEd25519(
                publicKey: expectedPublicKey,
                message: Data(),
                signature: expectedSignature
            )
        )
    }

    func testRandomBytesRejectsNegativeReturnsEmptyForZeroAndExactCountOtherwise() throws {
        let provider = CryptoKitSyncProvider()

        XCTAssertNil(provider.randomBytes(count: -1))
        XCTAssertEqual(provider.randomBytes(count: 0), Data())
        XCTAssertEqual(try XCTUnwrap(provider.randomBytes(count: 32)).count, 32)
    }

    func testSha256MatchesFIPSVector() {
        let provider = CryptoKitSyncProvider()

        XCTAssertEqual(
            provider.sha256(message: Data("abc".utf8))?.hex,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        )
    }

    func testHmacSha256MatchesRFC4231Vector() {
        let provider = CryptoKitSyncProvider()
        let result = provider.hmacSha256(
            key: Data(repeating: 0x0b, count: 20),
            message: Data("Hi There".utf8)
        )

        XCTAssertEqual(result?.hex, "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7")
    }

    func testAesGcmMatchesNISTVectorAndRejectsAlteredTag() {
        let provider = CryptoKitSyncProvider()
        let key = Data(repeating: 0, count: 16)
        let nonce = Data(repeating: 0, count: 12)
        let plaintext = Data(repeating: 0, count: 16)
        let expected = Data(hex: "0388dace60b6a392f328c2b971b2fe78ab6e47d42cec13bdf53a67b21257bddf")
        let sealed = provider.sealAesGcm(key: key, nonce: nonce, authenticatedData: Data(), plaintext: plaintext)

        XCTAssertEqual(sealed, expected)
        XCTAssertEqual(
            provider.openAesGcm(key: key, nonce: nonce, authenticatedData: Data(), ciphertextAndTag: expected),
            plaintext
        )
        var altered = expected
        altered[altered.index(before: altered.endIndex)] ^= 1
        XCTAssertNil(provider.openAesGcm(key: key, nonce: nonce, authenticatedData: Data(), ciphertextAndTag: altered))
    }

    func testEd25519GeneratedKeySignsVerifiesAndRetires() throws {
        let provider = CryptoKitSyncProvider()
        let key = try XCTUnwrap(provider.createSigningKey())
        let message = Data("posato-format-one".utf8)
        let publicKey = try XCTUnwrap(key.publicKey())
        let signature = try XCTUnwrap(key.sign(message: message))

        XCTAssertTrue(provider.verifyEd25519(publicKey: publicKey, message: message, signature: signature))
        XCTAssertFalse(provider.verifyEd25519(publicKey: publicKey, message: Data("altered".utf8), signature: signature))
        key.close()
        XCTAssertNil(key.sign(message: message))
    }
}

private extension Data {
    init(hex: String) {
        self.init(stride(from: 0, to: hex.count, by: 2).map { offset in
            let start = hex.index(hex.startIndex, offsetBy: offset)
            let end = hex.index(start, offsetBy: 2)

            return UInt8(hex[start..<end], radix: 16)!
        })
    }

    var hex: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
