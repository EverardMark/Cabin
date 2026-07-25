import Foundation

enum AppConfig {
    /// Base URL of the Cabin API.
    ///
    /// The iOS Simulator shares the Mac's network, so `localhost` works out of the box.
    /// For a **physical device**, replace this with your Mac's LAN IP,
    /// e.g. `http://192.168.1.20:8080`.
    static let baseURL = URL(string: "http://localhost:8080")!
}
