import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header

                Button(role: .destructive) {
                    appState.logout()
                } label: {
                    Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)

                Text("My listings").font(.title3.bold()).padding(.top, 4)

                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 20)
                } else if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                } else if listings.isEmpty {
                    Text(appState.currentUser?.isAgent == true
                         ? "You haven't posted any listings yet. Tap Post to add your first one."
                         : "Only agent accounts can post listings. Register as an agent to start posting.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(listings) { listing in
                        NavigationLink(value: listing.id) {
                            ListingCard(listing: listing)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Profile")
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private var header: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(Color.cabinForest).frame(width: 56, height: 56)
                Text(appState.currentUser?.name.first.map { String($0).uppercased() } ?? "?")
                    .font(.title3.bold())
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(appState.currentUser?.name ?? "—").font(.title3.weight(.semibold))
                    if appState.currentUser?.isAgent == true { AgentBadge() }
                }
                Text(appState.currentUser?.email ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            listings = try await appState.api.myListings().listings
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}
