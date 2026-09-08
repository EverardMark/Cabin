import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var savedSearches: [SavedSearch] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showEditProfile = false
    @State private var showPost = false
    @State private var verifying = false
    @State private var verificationMessage: String?
    @State private var showPhoneSheet = false
    @State private var showLogoutConfirm = false

    private var user: User? { appState.currentUser }
    private var activeListings: [Listing] { listings.filter { $0.status == "active" } }

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                AppMark()
            } title: {
                Text("Profile").font(.softScreenTitle)
            } trailing: {
                Menu {
                    Button { showEditProfile = true } label: { Label("Edit profile", systemImage: "pencil") }
                    Button { showPost = true } label: { Label("Post a listing", systemImage: "plus") }
                    Divider()
                    Button(role: .destructive) { showLogoutConfirm = true } label: {
                        Label("Log out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                } label: {
                    ZStack {
                        Circle().fill(Color.white.opacity(0.75))
                        Image(systemName: "gearshape").font(.system(size: 20, weight: .regular)).foregroundStyle(Color.softTextSoft)
                    }
                    .frame(width: 48, height: 48)
                    .softShadow(.circle)
                }
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    identityCard

                    HStack(spacing: 12) {
                        SoftTile(systemImage: "house", label: "Active listings", value: "\(activeListings.count)")
                        SoftTile(systemImage: "calendar", label: "Requests to answer", value: "\(appState.summary.pendingViewingRequests)")
                    }

                    NavigationLink { SavedSearchesView() } label: {
                        SoftRow {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Saved searches").font(.softBody)
                                Text(savedSearches.first?.name ?? "Get alerted when new listings match")
                                    .font(.soft(14)).foregroundStyle(Color.softSecondary).lineLimit(1)
                            }
                            .padding(.leading, 8)
                            Spacer(minLength: 4)
                            let newMatches = savedSearches.reduce(0) { $0 + $1.newMatches }
                            if newMatches > 0 {
                                VTag(text: "\(newMatches) new", systemImage: nil, tint: .softAccent)
                            } else {
                                chevron
                            }
                        }
                    }
                    .buttonStyle(SoftPressStyle())

                    phoneRow

                    if user?.verificationStatus != .verified {
                        verificationRow
                    }

                    PrimaryButton(title: "Post a listing", systemImage: "plus", large: true) { showPost = true }
                        .padding(.top, 4)

                    Text("My listings")
                        .font(.softSmall).foregroundStyle(Color.softSecondary)
                        .padding(.horizontal, 8).padding(.top, 10)

                    if loading {
                        ProgressView().tint(Color.softInk).frame(maxWidth: .infinity).padding(.top, 20)
                    } else if let errorMessage {
                        SoftError(message: errorMessage)
                    } else if listings.isEmpty {
                        Text("You haven't posted anything yet. Owners, agents and renters can all post.")
                            .font(.soft(15)).foregroundStyle(Color.softSecondary)
                            .padding(.horizontal, 8)
                    } else {
                        ForEach(listings) { listing in
                            NavigationLink(value: listing.id) {
                                MyListingCard(listing: listing)
                            }
                            .buttonStyle(SoftPressStyle())
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 120)
            }
        }
        .softScreen()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .sheet(isPresented: $showEditProfile) { EditProfileSheet() }
        .sheet(isPresented: $showPhoneSheet) { PhoneVerificationSheet() }
        .sheet(isPresented: $showPost, onDismiss: { Task { await load() } }) {
            NavigationStack { CreateListingView() }
        }
        .alert("Verification", isPresented: .constant(verificationMessage != nil)) {
            Button("OK") { verificationMessage = nil }
        } message: {
            Text(verificationMessage ?? "")
        }
        .confirmationDialog("Log out of Cabin?", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("Log out", role: .destructive) { appState.logout() }
        }
        .task {
            await load()
            await appState.refreshSummary()
        }
        .refreshable { await load() }
    }

    // MARK: - Pieces

    private var identityCard: some View {
        SoftCard {
            HStack(spacing: 16) {
                SoftAvatar(name: user?.name ?? "?", size: 72)
                VStack(alignment: .leading, spacing: 4) {
                    Text(user?.name ?? "—").font(.softScreenTitle).lineLimit(1)
                    Text(subtitle).font(.softSmall).foregroundStyle(Color.softSecondary).lineLimit(1)
                    HStack(spacing: 6) {
                        if user?.isVerified == true {
                            VTag(text: "Account verified")
                        } else {
                            OTag(text: user?.verificationStatus == .pending ? "Under review" : "Not verified")
                        }
                        if let u = user, u.ratingCount > 0 {
                            OTag(text: String(format: "★ %.1f · %d", u.ratingAvg, u.ratingCount))
                        }
                    }
                    .padding(.top, 4)
                }
            }
        }
    }

    private var subtitle: String {
        guard let user else { return "" }
        if user.isAgent {
            return user.licenseNo.isEmpty ? "Real estate agent" : "Licensed agent · \(user.licenseNo)"
        }
        return user.email
    }

    /// A confirmed number is the prerequisite for the badge: it is meant to mean
    /// somebody is reachable, not that they typed a number in.
    private var phoneRow: some View {
        Button { showPhoneSheet = true } label: {
            SoftRow {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Mobile number").font(.softBody)
                    Text(phoneSubtitle)
                        .font(.soft(14))
                        .foregroundStyle(user?.phoneVerified == true ? Color.softSecondary : Color.softClay)
                        .lineLimit(1)
                }
                .padding(.leading, 8)
                Spacer(minLength: 4)
                chevron
            }
        }
        .buttonStyle(SoftPressStyle())
    }

    private var phoneSubtitle: String {
        guard let user else { return "" }
        if user.phoneVerified {
            return "\(AuthFlowView.masked(user.phone)) · confirmed by SMS"
        }
        return user.phone.isEmpty ? "Add and confirm your number" : "\(user.phone) · not confirmed yet"
    }

    private var verificationRow: some View {
        SoftRow {
            VStack(alignment: .leading, spacing: 2) {
                Text("Identity verification").font(.softBody)
                Text(user?.verificationNotes.isEmpty == false
                     ? user!.verificationNotes
                     : (user?.phoneVerified == true
                        ? (user?.isAgent == true ? "Needs your PRC licence number and a short review." : "A short automated review of your account.")
                        : "Confirm your mobile number first."))
                    .font(.soft(14)).foregroundStyle(Color.softSecondary)
                    .lineLimit(2)
            }
            .padding(.leading, 8)
            Spacer(minLength: 4)
            if user?.phoneVerified == true && user?.verificationStatus != .pending {
                Button {
                    Task { await requestVerification() }
                } label: {
                    Group {
                        if verifying { ProgressView().tint(.white) } else { Text("Request") }
                    }
                    .font(.soft(13, .regular))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16).padding(.vertical, 9)
                    .background(Color.softInk, in: Capsule())
                }
                .buttonStyle(SoftPressStyle())
                .disabled(verifying)
            } else {
                OTag(text: user?.verificationStatus == .pending ? "Reviewing" : "Optional")
            }
        }
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 14, weight: .light))
            .foregroundStyle(Color.softMuted)
            .padding(.trailing, 4)
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
            savedSearches = (try? await appState.api.savedSearches().searches) ?? []
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

/// The owner's own listing: compact card plus the screening verdict when
/// something needs fixing — owners must see why a listing was held back.
struct MyListingCard: View {
    let listing: Listing

    var body: some View {
        SoftCard(padding: 12) {
            VStack(alignment: .leading, spacing: 0) {
                SoftPhoto(url: listing.images.first?.url)
                    .frame(height: 160)
                    .frame(maxWidth: .infinity)
                    .overlay(alignment: .topLeading) { PhotoTags(listing: listing).padding(12) }

                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text(listing.title).font(.soft(18)).lineLimit(1)
                    Spacer(minLength: 0)
                    PriceText(price: listing.price, listingType: listing.listingType, size: 16)
                }
                .padding(.horizontal, 8).padding(.top, 12)

                if listing.verificationStatus == .flagged || listing.verificationStatus == .rejected {
                    Text(listing.verificationSummary.isEmpty ? listing.verificationStatus.label : listing.verificationSummary)
                        .font(.soft(14)).foregroundStyle(Color.softClay)
                        .lineLimit(3)
                        .padding(.horizontal, 8).padding(.top, 4)
                } else if listing.isStale {
                    Text("Not confirmed recently — open it and tap “still available”.")
                        .font(.soft(14)).foregroundStyle(Color.softClay)
                        .padding(.horizontal, 8).padding(.top, 4)
                }
                Color.clear.frame(height: 4)
            }
        }
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
