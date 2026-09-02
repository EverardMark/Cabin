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
    @Environment(AppState.self) private var appState

    var body: some View {
        TabView {
            NavigationStack {
                ListingsView()
            }
            .tabItem { Label("Browse", systemImage: "magnifyingglass") }

            NavigationStack {
                MessagesView()
            }
            .tabItem { Label("Messages", systemImage: "bubble.left.and.bubble.right") }
            .badge(appState.summary.unreadMessages)

            // Posting is open to everyone: owners are the largest group of
            // posters in the survey, and no respondent wanted an agents-only
            // marketplace.
            NavigationStack {
                CreateListingView()
            }
            .tabItem { Label("Post", systemImage: "plus.circle.fill") }

            NavigationStack {
                ViewingsView()
            }
            .tabItem { Label("Viewings", systemImage: "calendar") }
            .badge(appState.summary.pendingViewingRequests)

            NavigationStack {
                ProfileView()
            }
            .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
        .task { await appState.refreshSummary() }
    }
}
