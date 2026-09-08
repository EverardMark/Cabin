import Foundation

enum AppConfig {
    /// Base URL of the Cabin API.
    ///
    /// Points at the deployed server so simulators and physical devices work without
    /// running the Go server locally. To develop against a local server instead, use
    /// `http://localhost:8080` (simulator) or your Mac's LAN IP (physical device).
    static let baseURL = URL(string: "http://47.129.37.21:8080")!
}
