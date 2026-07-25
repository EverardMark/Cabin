import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var verifyBusy = false
    @State private var verifyError: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header

                verificationSection

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
                    Text("You haven't posted any listings yet. Tap Post to add your first one.")
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
                    if appState.currentUser?.verified == true { VerifiedBadge() }
                }
                Text(appState.currentUser?.email ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
    }

    @ViewBuilder
    private var verificationSection: some View {
        if appState.currentUser?.verified == true {
            Label("Your account is verified", systemImage: "checkmark.seal.fill")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Color.cabinForest)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color.cabinForest.opacity(0.1), in: RoundedRectangle(cornerRadius: 12))
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Label("Get verified", systemImage: "checkmark.seal").font(.headline)
                Text("Verified owners earn a trust badge and stand out to buyers — the #1 thing surveyed users asked for.")
                    .font(.caption).foregroundStyle(.secondary)
                if let verifyError {
                    Text(verifyError).font(.caption).foregroundStyle(.red)
                }
                Button {
                    Task {
                        verifyBusy = true
                        verifyError = nil
                        do { try await appState.verify() } catch { verifyError = error.localizedDescription }
                        verifyBusy = false
                    }
                } label: {
                    if verifyBusy {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Verify my account").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(verifyBusy)
            }
            .padding(14)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
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
