import Foundation

/// Remembers recently used nonces so a captured request cannot be replayed.
///
/// Only nonces inside the freshness window need remembering: anything older is
/// already rejected on its timestamp, so entries are pruned rather than kept forever.
actor NonceCache {

    private let window: TimeInterval
    private let capacity: Int
    private var seen: [String: Date] = [:]

    init(window: TimeInterval, capacity: Int = 20_000) {
        self.window = window
        self.capacity = capacity
    }

    /// Records the nonce and reports whether it had already been used.
    func claim(_ nonce: String, at date: Date) -> Bool {
        prune(before: date.addingTimeInterval(-window))
        guard seen[nonce] == nil else { return false }
        enforceCapacity()
        seen[nonce] = date
        return true
    }

    private func prune(before cutoff: Date) {
        seen = seen.filter { $0.value > cutoff }
    }

    /// Pruning alone is bounded only by the window, so a paired device could hold
    /// unbounded nonces inside one window. Dropping the oldest keeps that finite. It
    /// weakens replay protection for the evicted entries, which is the better trade
    /// against unbounded growth, and only a paired device can reach this at all.
    private func enforceCapacity() {
        guard seen.count >= capacity else { return }
        let excess = seen.count - capacity + 1
        seen.sorted { $0.value < $1.value }
            .prefix(excess)
            .forEach { seen.removeValue(forKey: $0.key) }
    }
}
