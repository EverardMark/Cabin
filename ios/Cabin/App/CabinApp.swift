import SwiftUI

@main
struct CabinApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
                .tint(.cabinForest)
        }
    }
}
