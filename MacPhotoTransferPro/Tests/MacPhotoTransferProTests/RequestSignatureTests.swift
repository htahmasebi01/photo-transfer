import Foundation
import XCTest
@testable import MacPhotoTransferPro

final class RequestSignatureTests: XCTestCase {

    /// Locks the wire format down with a vector the Android side asserts too. If this
    /// breaks, one platform changed the canonical string and pairing will stop working.
    func testCanonicalStringMatchesTheSharedVector() {
        let canonicalString = RequestSignature.canonicalString(
            method: "POST",
            path: "/v1/transfers",
            timestamp: 1_700_000_000,
            nonce: "test-nonce",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data())
        )

        XCTAssertEqual(
            canonicalString,
            """
            POST
            /v1/transfers
            1700000000
            test-nonce
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            """
        )
    }

    func testSignatureMatchesTheSharedVector() {
        let canonicalString = RequestSignature.canonicalString(
            method: "POST",
            path: "/v1/transfers",
            timestamp: 1_700_000_000,
            nonce: "test-nonce",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data())
        )

        let signature = RequestSignature.sign(
            canonicalString: canonicalString,
            secret: Data(repeating: 7, count: 32)
        )

        XCTAssertEqual(signature, "m6f0JxKN3W67v2Hm+kcDL8TizBjjtdOiiyifGCYhn9s=")
    }

    /// The other half of the shared contract. If only one platform changes this string,
    /// the sender concludes the receiver is an impostor and refuses to send anything.
    func testReceiverProofMatchesTheSharedVector() {
        let proofString = RequestSignature.receiverProofString(
            method: "POST",
            path: "/v1/verify",
            nonce: "test-nonce"
        )

        XCTAssertEqual(
            proofString,
            """
            PT-RESPONSE-v1
            POST
            /v1/verify
            test-nonce
            """
        )
        XCTAssertEqual(
            RequestSignature.sign(canonicalString: proofString, secret: Data(repeating: 7, count: 32)),
            "xRGJtiA1mw6eZBFsk9HYA8N/NTpSKr5pMTNEmia0lpU="
        )
    }

    /// A request signature and a proof over the same request must never coincide, or a
    /// captured request could be replayed back as the receiver's answer.
    func testProofIsInADifferentNamespaceFromARequestSignature() {
        let request = RequestSignature.canonicalString(
            method: "POST",
            path: "/v1/verify",
            timestamp: 1_700_000_000,
            nonce: "test-nonce",
            bodySha256Hex: RequestSignature.sha256Hex(of: Data())
        )
        let proof = RequestSignature.receiverProofString(
            method: "POST",
            path: "/v1/verify",
            nonce: "test-nonce"
        )

        XCTAssertNotEqual(request, proof)
    }

    func testMethodIsUppercasedBeforeSigning() {
        let lowercase = RequestSignature.canonicalString(
            method: "post",
            path: "/v1/transfers",
            timestamp: 1,
            nonce: "n",
            bodySha256Hex: "hash"
        )
        let uppercase = RequestSignature.canonicalString(
            method: "POST",
            path: "/v1/transfers",
            timestamp: 1,
            nonce: "n",
            bodySha256Hex: "hash"
        )

        XCTAssertEqual(lowercase, uppercase)
    }

    func testValidationRejectsAnotherSecret() {
        let canonicalString = "POST\n/v1/transfers\n1\nn\nhash"
        let signature = RequestSignature.sign(
            canonicalString: canonicalString,
            secret: Data(repeating: 1, count: 32)
        )

        XCTAssertFalse(RequestSignature.isValid(
            signatureBase64: signature,
            canonicalString: canonicalString,
            secret: Data(repeating: 2, count: 32)
        ))
    }

    func testValidationRejectsMalformedBase64() {
        XCTAssertFalse(RequestSignature.isValid(
            signatureBase64: "not base64 !!",
            canonicalString: "POST\n/v1/transfers\n1\nn\nhash",
            secret: Data(repeating: 1, count: 32)
        ))
    }
}
