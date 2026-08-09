import Foundation

/// Advertises the receiver over Bonjour so Android NSD can discover it.
///
/// NetService is deprecated but remains the only high-level API that can
/// advertise a port owned by another listener (FlyingFox holds the socket,
/// so NWListener's built-in advertisement is not usable here).
final class BonjourAdvertiser {

    static let serviceType = "_androidphototransfer._tcp."

    private var service: NetService?

    func start(name: String, port: UInt16) {
        stop()
        let service = NetService(
            domain: "local.",
            type: Self.serviceType,
            name: name,
            port: Int32(port)
        )
        service.publish()
        self.service = service
    }

    func stop() {
        service?.stop()
        service = nil
    }
}
