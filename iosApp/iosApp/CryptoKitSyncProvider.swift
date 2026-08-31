import CryptoKit
import Foundation
import PosatoShared
import Security

final class CryptoKitSyncProvider: IosCryptoProvider {
    func randomBytes(count: Int32) -> Data? {
        guard count >= 0 else {
            return nil
        }
        var bytes = Data(count: Int(count))
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
        }

        return status == errSecSuccess ? bytes : nil
    }

    func hmacSha256(key: Data, message: Data) -> Data? {
        let authenticationCode = HMAC<SHA256>.authenticationCode(
            for: message,
            using: SymmetricKey(data: key)
        )

        return Data(authenticationCode)
    }

    func sha256(message: Data) -> Data? {
        return Data(SHA256.hash(data: message))
    }

    func sealAesGcm(
        key: Data,
        nonce: Data,
        authenticatedData: Data,
        plaintext: Data
    ) -> Data? {
        do {
            let sealed = try AES.GCM.seal(
                plaintext,
                using: SymmetricKey(data: key),
                nonce: try AES.GCM.Nonce(data: nonce),
                authenticating: authenticatedData
            )

            return sealed.ciphertext + sealed.tag
        } catch {
            return nil
        }
    }

    func openAesGcm(
        key: Data,
        nonce: Data,
        authenticatedData: Data,
        ciphertextAndTag: Data
    ) -> Data? {
        guard ciphertextAndTag.count >= 16 else {
            return nil
        }
        do {
            let tagStart = ciphertextAndTag.index(ciphertextAndTag.endIndex, offsetBy: -16)
            let sealed = try AES.GCM.SealedBox(
                nonce: try AES.GCM.Nonce(data: nonce),
                ciphertext: ciphertextAndTag[..<tagStart],
                tag: ciphertextAndTag[tagStart...]
            )

            return try AES.GCM.open(
                sealed,
                using: SymmetricKey(data: key),
                authenticating: authenticatedData
            )
        } catch {
            return nil
        }
    }

    func createSigningKey() -> (any IosSigningKey)? {
        return CryptoKitSigningKey()
    }

    func verifyEd25519(publicKey: Data, message: Data, signature: Data) -> Bool {
        do {
            let key = try Curve25519.Signing.PublicKey(rawRepresentation: publicKey)

            return key.isValidSignature(signature, for: message)
        } catch {
            return false
        }
    }
}

private final class CryptoKitSigningKey: IosSigningKey {
    private var key: Curve25519.Signing.PrivateKey? = Curve25519.Signing.PrivateKey()

    func publicKey() -> Data? {
        return key?.publicKey.rawRepresentation
    }

    func sign(message: Data) -> Data? {
        return try? key?.signature(for: message)
    }

    func close() {
        key = nil
    }
}
