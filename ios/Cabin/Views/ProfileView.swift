import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showEditProfile = false
    @State private var verifying = false
    @State private var verificationMessage: String?

    private var user: User? { appState.currentUser }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                verificationCard
                shortcuts

                Text("My listings").font(.title3.bold()).padding(.top, 4)

                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 20)
                } else if let errorMessage {
                    Text(errorMessage).foregroundStyle(.red)
                } else if listings.isEmpty {
                    Text("You haven't posted anything yet. Tap Post to add your first listing — owners, agents and renters can all post.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(listings) { listing in
                        NavigationLink(value: listing.id) {
                            VStack(alignment: .leading, spacing: 6) {
                                ListingCard(listing: listing)
                                // Owners must see why their own listing was held back.
                                if listing.verificationStatus == .rejected || listing.verificationStatus == .flagged {
                                    Label(listing.verificationSummary.isEmpty
                                          ? listing.verificationStatus.label
                                          : listing.verificationSummary,
                                          systemImage: "exclamationmark.bubble")
                                        .font(.caption)
                                        .foregroundStyle(listing.verificationStatus.tint)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                Button(role: .destructive) {
                    appState.logout()
                } label: {
                    Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .padding(.top, 8)
            }
            .padding(16)
        }
        .navigationTitle("Profile")
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .sheet(isPresented: $showEditProfile) { EditProfileSheet() }
        .alert("Verification", isPresented: .constant(verificationMessage != nil)) {
            Button("OK") { verificationMessage = nil }
        } message: {
            Text(verificationMessage ?? "")
        }
        .task {
            await load()
            await appState.refreshSummary()
        }
        .refreshable { await load() }
    }

    private var header: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(Color.cabinForest).frame(width: 56, height: 56)
                Text(user?.name.first.map { String($0).uppercased() } ?? "?")
                    .font(.title3.bold())
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(user?.name ?? "—").font(.title3.weight(.semibold))
                    if user?.isAgent == true { AgentBadge() }
                }
                Text(user?.email ?? "").font(.subheadline).foregroundStyle(.secondary)
                RatingStars(rating: user?.ratingAvg ?? 0, count: user?.ratingCount ?? 0)
            }
            Spacer()
            Button("Edit") { showEditProfile = true }.font(.subheadline)
        }
    }

    /// The account's own verification state, and the way to earn the badge.
    @ViewBuilder
    private var verificationCard: some View {
        let status = user?.verificationStatus ?? .unverified
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: status.symbol).foregroundStyle(status.tint)
                Text(status == .verified ? "Your account is verified" : "Get verified")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                VerificationBadge(status: status)
            }

            if let notes = user?.verificationNotes, !notes.isEmpty {
                Text(notes).font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text("Verified accounts get a badge on every listing they post. 86% of people we surveyed said verification is what decides whether they trust a listing.")
                    .font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if status != .verified {
                Button {
                    Task { await requestVerification() }
                } label: {
                    if verifying {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Request verification").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.regular)
                .disabled(verifying)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(status.tint.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
    }

    private var shortcuts: some View {
        VStack(spacing: 0) {
            NavigationLink { SavedSearchesView() } label: {
                Label("Saved searches", systemImage: "bell.badge")
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 12)
            }
            Divider()
            NavigationLink { ViewingsView() } label: {
                HStack {
                    Label("My viewings", systemImage: "calendar")
                    Spacer()
                    if appState.summary.pendingViewingRequests > 0 {
                        Text("\(appState.summary.pendingViewingRequests) to answer")
                            .font(.caption).foregroundStyle(Color.cabinClay)
                    }
                }
                .padding(.vertical, 12)
            }
        }
        .padding(.horizontal, 14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private func requestVerification() async {
        verifying = true
        do {
            let verdict = try await appState.requestVerification()
            verificationMessage = verdict?.summary ?? "We've reviewed your account."
        } catch {
            verificationMessage = error.localizedDescription
        }
        verifying = false
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

struct EditProfileSheet: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var phone = ""
    @State private var bio = ""
    @State private var licenseNo = ""
    @State private var isAgent = false
    @State private var saving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("About you") {
                    TextField("Full name", text: $name).textContentType(.name)
                    TextField("Mobile number", text: $phone)
                        .textContentType(.telephoneNumber).keyboardType(.phonePad)
                    TextField("Short bio", text: $bio, axis: .vertical).lineLimit(2...5)
                }
                Section("Account type") {
                    Toggle("I'm a real estate agent", isOn: $isAgent)
                    if isAgent {
                        TextField("PRC licence number", text: $licenseNo)
                            .textInputAutocapitalization(.characters)
                        Text("Agents need a licence number to be verified.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.footnote).foregroundStyle(.red) }
                }
                Section {
                    Text("Changing your number means you'll need to verify again.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Edit profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }.disabled(saving)
                }
            }
            .onAppear {
                let user = appState.currentUser
                name = user?.name ?? ""
                phone = user?.phone ?? ""
                bio = user?.bio ?? ""
                licenseNo = user?.licenseNo ?? ""
                isAgent = user?.isAgent ?? false
            }
        }
    }

    private func save() async {
        saving = true
        errorMessage = nil
        do {
            try await appState.updateProfile(ProfileRequest(
                name: name, phone: phone, bio: bio,
                licenseNo: licenseNo, role: isAgent ? "agent" : "user"
            ))
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        saving = false
    }
}
