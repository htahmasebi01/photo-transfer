import CryptoKit
import Foundation

/// The bytes a client signs, and the receiver re-derives, for every authenticated request.
///
/// Both platforms must build this string identically, so the field order and the
/// newline separator are part of the wire protocol.
enum RequestSignature {

    static func canonicalString(
        method: String,
        path: String,
        timestamp: Int64,
        nonce: String,
        bodySha256Hex: String
    ) -> String {
        [
            method.uppercased(),
            path,
            String(timestamp),
            nonce,
            bodySha256Hex
        ].joined(separator: "\n")
    }

    /// What the receiver signs to prove it holds the pairing secret.
    ///
    /// HMAC only authenticates the sender, so without this a device that reads a
    /// `receiverId` off the network can advertise the same id, be treated as already
    /// paired, and collect photos. Binding the proof to the caller's own nonce makes it
    /// unforgeable and unreplayable by anyone without the secret.
    ///
    /// The `PT-RESPONSE-v1` prefix keeps this in a different namespace from a request
    /// signature, so a captured request signature can never be presented as a proof.
    static func receiverProofString(method: String, path: String, nonce: String) -> String {
        [
            "PT-RESPONSE-v1",
            method.uppercased(),
            path,
            nonce
        ].joined(separator: "\n")
    }

    static func sha256Hex(of data: Data) -> String {
        SHA256.hash(data: data)
            .map { String(format: "%02x", $0) }
            .joined()
    }

    static func sign(canonicalString: String, secret: Data) -> String {
        let code = HMAC<SHA256>.authenticationCode(
            for: Data(canonicalString.utf8),
            using: SymmetricKey(data: secret)
        )
        return Data(code).base64EncodedString()
    }

    /// Constant-time comparison, so a caller cannot learn the expected signature byte by byte.
    static func isValid(signatureBase64: String, canonicalString: String, secret: Data) -> Bool {
        guard let provided = Data(base64Encoded: signatureBase64) else { return false }
        return HMAC<SHA256>.isValidAuthenticationCode(
            provided,
            authenticating: Data(canonicalString.utf8),
            using: SymmetricKey(data: secret)
        )
    }
}
