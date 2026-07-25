import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.isLoggedIn {
            MainTabView()
        } else {
            AuthView()
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                ListingsView()
            }
            .tabItem { Label("Browse", systemImage: "magnifyingglass") }

            NavigationStack {
                CreateListingView()
            }
            .tabItem { Label("Post", systemImage: "plus.circle.fill") }

            NavigationStack {
                ProfileView()
            }
            .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}
