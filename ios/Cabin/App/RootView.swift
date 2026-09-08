import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.isLoggedIn && !appState.onboarding {
            MainTabView()
        } else {
            AuthFlowView()
        }
    }
}

/// Five tabs behind one floating pill bar. Every tab keeps its own
/// NavigationStack alive so switching back restores scroll position and any
/// pushed screen.
struct MainTabView: View {
    @Environment(AppState.self) private var appState
    @State private var chrome = ChromeState()

    var body: some View {
        @Bindable var chrome = chrome
        ZStack(alignment: .bottom) {
            ZStack {
                tabContent(.home) { ListingsView() }
                tabContent(.chat) { MessagesView() }
                tabContent(.map) { MapSearchView() }
                tabContent(.viewings) { ViewingsView() }
                tabContent(.profile) { ProfileView() }
            }

            if !chrome.isTabBarHidden {
                SoftTabBar(selected: $chrome.selectedTab, badges: [
                    .chat: appState.summary.unreadMessages,
                    .viewings: appState.summary.pendingViewingRequests,
                ])
                .padding(.bottom, 8)
                .ignoresSafeArea(.keyboard, edges: .bottom)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeOut(duration: 0.2), value: chrome.isTabBarHidden)
        .environment(chrome)
        .background(SoftBackground())
        .task { await appState.refreshSummary() }
    }

    private func tabContent<Content: View>(_ which: SoftTab, @ViewBuilder content: () -> Content) -> some View {
        let active = chrome.selectedTab == which
        return NavigationStack { content() }
            .opacity(active ? 1 : 0)
            .allowsHitTesting(active)
            .accessibilityHidden(!active)
    }
}
