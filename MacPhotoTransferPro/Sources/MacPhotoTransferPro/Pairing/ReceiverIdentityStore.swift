import Foundation

/// Supplies this Mac's receiver id, which senders use to look up which pairing to sign with.
///
/// It must survive restarts, otherwise every launch would look like a new receiver and
/// invalidate existing pairings.
struct ReceiverIdentityStore {

    private static let receiverIdKey = "receiverId"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func receiverId() -> String {
        if let existing = defaults.string(forKey: Self.receiverIdKey) {
            return existing
        }
        let generated = UUID().uuidString.lowercased()
        defaults.set(generated, forKey: Self.receiverIdKey)
        return generated
    }
}
