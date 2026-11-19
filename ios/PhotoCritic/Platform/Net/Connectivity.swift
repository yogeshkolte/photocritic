import Foundation
import Network
import Observation

/// Whether the device has a network path, for the offline banner and the
/// disabled Send button (file 38).
@MainActor
@Observable
final class Connectivity {
    private(set) var online = true
    @ObservationIgnored private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.online = online }
        }
        monitor.start(queue: DispatchQueue(label: "photocritic.connectivity"))
    }

    deinit { monitor.cancel() }
}
