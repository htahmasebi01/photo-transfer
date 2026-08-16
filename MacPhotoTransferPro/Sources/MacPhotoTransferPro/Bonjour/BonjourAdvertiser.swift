import Foundation

/// Advertises the receiver over Bonjour so Android NSD can discover it.
///
/// NetService is deprecated but remains the only high-level API that can
/// advertise a port owned by another listener (FlyingFox holds the socket,
/// so NWListener's built-in advertisement is not usable here).
final class BonjourAdvertiser {

    static let serviceType = "_androidphototransfer._tcp."

    private var service: NetService?

    /// Publishing `receiverId` in the TXT record lets a sender pick the right pairing
    /// straight from discovery, with no unauthenticated round trip to `/v1/info`.
    func start(name: String, port: UInt16, receiverId: String) {
        stop()
        let service = NetService(
            domain: "local.",
            type: Self.serviceType,
            name: name,
            port: Int32(port)
        )
        service.setTXTRecord(NetService.data(fromTXTRecord: [
            "receiverId": Data(receiverId.utf8),
            "protocolVersion": Data(String(TransferProtocol.version).utf8)
        ]))
        service.publish()
        self.service = service
    }

    func stop() {
        service?.stop()
        service = nil
    }
}
