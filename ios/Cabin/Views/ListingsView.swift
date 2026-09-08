import SwiftUI

/// Home: the verified feed. The first listing gets the big card with price and
/// trust-score tiles; the rest are compact photo cards.
struct ListingsView: View {
    @Environment(AppState.self) private var appState
    @Environment(ChromeState.self) private var chrome

    @State private var listings: [Listing] = []
    @State private var filters = ListingFilters()
    @State private var loading = false
    @State private var errorMessage: String?
    @State private var showFilters = false
    @State private var showSaveSearch = false
    @State private var saveSearchName = ""
    @State private var saveConfirmation: String?
    @State private var openedConversation: Conversation?
    @State private var bookingListing: Listing?
    @State private var actionError: String?

    private let propertyTypes = ["house", "apartment", "condo", "townhouse", "land"]

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                AppMark()
            } title: {
                EmptyView()
            } trailing: {
                HStack(spacing: 10) {
                    CircleButton(systemImage: "magnifyingglass", filled: showFilters) {
                        withAnimation(.easeOut(duration: 0.2)) { showFilters.toggle() }
                    }
                    CircleButton(systemImage: "bubble.left", badge: appState.summary.unreadMessages > 0) {
                        chrome.selectedTab = .chat
                    }
                }
            }

            ScrollView {
                LazyVStack(spacing: 16) {
                    if showFilters { filterPanel }

                    if loading && listings.isEmpty {
                        ProgressView().tint(Color.softInk).padding(.top, 40)
                    } else if let errorMessage, listings.isEmpty {
                        SoftEmpty(systemImage: "wifi.slash", title: "Couldn't load listings",
                                  message: errorMessage, actionTitle: "Retry") { Task { await load() } }
                    } else if listings.isEmpty {
                        SoftEmpty(systemImage: "magnifyingglass", title: "No listings found",
                                  message: filters.verifiedOnly
                                      ? "Nothing verified matches these filters yet. Turn off “Verified only” to include listings still being screened."
                                      : "Try adjusting your search or filters.",
                                  actionTitle: filters.verifiedOnly ? "Include unverified" : nil) {
                            filters.verifiedOnly = false
                            Task { await load() }
                        }
                    } else {
                        ForEach(Array(listings.enumerated()), id: \.element.id) { index, listing in
                            if index == 0 {
                                HeroListingCard(listing: listing,
                                                isMine: listing.userId == appState.currentUser?.id,
                                                onMessage: { Task { await startChat(listing) } },
                                                onBook: { bookingListing = listing })
                            } else {
                                NavigationLink(value: listing.id) {
                                    CompactListingCard(listing: listing)
                                }
                                .buttonStyle(SoftPressStyle())
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 22)
                .padding(.bottom, 120)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .softScreen()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .navigationDestination(item: $openedConversation) { conv in
            ChatView(conversation: conv)
        }
        .sheet(item: $bookingListing) { listing in
            BookViewingSheet(listing: listing)
        }
        .alert("Save this search", isPresented: $showSaveSearch) {
            TextField("Name", text: $saveSearchName)
            Button("Cancel", role: .cancel) {}
            Button("Save") { Task { await saveSearch() } }
        } message: {
            Text("We'll show you how many new listings match when you come back.")
        }
        .alert("Saved", isPresented: .constant(saveConfirmation != nil)) {
            Button("OK") { saveConfirmation = nil }
        } message: {
            Text(saveConfirmation ?? "")
        }
        .alert("Something went wrong", isPresented: .constant(actionError != nil)) {
            Button("OK") { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
        .task { await load() }
        .refreshable { await load() }
    }

    // MARK: - Search & filters

    private var filterPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass").font(.system(size: 18, weight: .light)).foregroundStyle(Color.softLabel)
                TextField("", text: $filters.query,
                          prompt: Text("Search city, title, address").font(.soft(17)).foregroundStyle(Color.softMuted))
                    .font(.soft(17))
                    .submitLabel(.search)
                    .onSubmit { Task { await load() } }
            }
            .padding(.horizontal, 18)
            .frame(height: 52)
            .background(Color.white, in: Capsule())
            .softShadow(.row)

            // The verified-only switch is the survey's most requested feature (79%); it defaults to on.
            SoftRow(fill: .softTile, shadow: false) {
                Image(systemName: "checkmark.seal").font(.system(size: 18, weight: .light)).foregroundStyle(Color.softTextSoft)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Verified listings only").font(.soft(16, .regular))
                    Text("Hides listings that haven't passed screening").font(.soft(13)).foregroundStyle(Color.softSecondary)
                }
                Spacer()
                Toggle("", isOn: $filters.verifiedOnly).labelsHidden().tint(Color.softInk)
                    .onChange(of: filters.verifiedOnly) { Task { await load() } }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    SoftChip(title: "All", selected: filters.listingType == nil) { set(\.listingType, nil) }
                    SoftChip(title: "Buy", selected: filters.listingType == "sale") { set(\.listingType, "sale") }
                    SoftChip(title: "Rent", selected: filters.listingType == "rent") { set(\.listingType, "rent") }
                    Rectangle().fill(Color.softDivider).frame(width: 1, height: 20).padding(.horizontal, 2)
                    SoftChip(title: "Any type", selected: filters.propertyType == nil) { set(\.propertyType, nil) }
                    ForEach(propertyTypes, id: \.self) { type in
                        SoftChip(title: Format.capitalized(type), selected: filters.propertyType == type) {
                            set(\.propertyType, type)
                        }
                    }
                }
                .padding(.horizontal, 2)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach([("recent", "Newest"), ("trusted", "Most trusted"),
                             ("price_asc", "Price ↑"), ("price_desc", "Price ↓")], id: \.0) { value, label in
                        SoftChip(title: label, selected: filters.sort == value) {
                            filters.sort = value
                            Task { await load() }
                        }
                    }
                    SoftChip(title: "Recently confirmed", selected: filters.excludeStale) {
                        filters.excludeStale.toggle()
                        Task { await load() }
                    }
                }
                .padding(.horizontal, 2)
            }

            SoftLink(title: "Save this search") {
                saveSearchName = defaultSearchName
                showSaveSearch = true
            }
            .padding(.leading, 6)
        }
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func set(_ key: WritableKeyPath<ListingFilters, String?>, _ value: String?) {
        filters[keyPath: key] = value
        Task { await load() }
    }

    private var defaultSearchName: String {
        var parts: [String] = []
        if !filters.city.isEmpty { parts.append(filters.city) }
        if !filters.query.isEmpty { parts.append(filters.query) }
        if let t = filters.propertyType { parts.append(Format.capitalized(t)) }
        if let t = filters.listingType { parts.append(t == "rent" ? "for rent" : "for sale") }
        return parts.isEmpty ? "My search" : parts.joined(separator: " · ")
    }

    private func saveSearch() async {
        do {
            _ = try await appState.api.saveSearch(name: saveSearchName, query: filters.queryString)
            saveConfirmation = "We'll flag new matches for “\(saveSearchName)” in your profile."
        } catch {
            saveConfirmation = error.localizedDescription
        }
    }

    private func startChat(_ listing: Listing) async {
        do {
            openedConversation = try await appState.api.startConversation(listingId: listing.id)
        } catch {
            actionError = error.localizedDescription
        }
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            listings = try await appState.api.listings(filters: filters).listings
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

// MARK: - Cards

/// Badges that sit on a listing photo: verification first, promotion second
/// and in white so it never reads as trust.
struct PhotoTags: View {
    let listing: Listing

    var body: some View {
        HStack(spacing: 6) {
            switch listing.verificationStatus {
            case .verified: VTag(text: "Verified")
            case .flagged:  VTag(text: "Check details", systemImage: "exclamationmark", tint: .softClay)
            case .rejected: VTag(text: "Failed screening", systemImage: "xmark", tint: .softRed)
            case .pending:  VTag(text: "Being screened", systemImage: "clock", light: true)
            case .unverified: VTag(text: "Not screened", systemImage: nil, light: true)
            }
            if listing.isFeatured { VTag(text: "Featured", systemImage: nil, light: true) }
        }
    }
}

/// The big first card: photo, title, meta, price + trust tiles, two actions.
struct HeroListingCard: View {
    let listing: Listing
    var isMine: Bool = false
    var onMessage: () -> Void
    var onBook: () -> Void

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 0) {
                NavigationLink(value: listing.id) {
                    VStack(alignment: .leading, spacing: 0) {
                        SoftPhoto(url: listing.images.first?.url)
                            .frame(height: 300)
                            .frame(maxWidth: .infinity)
                            .overlay(alignment: .topLeading) { PhotoTags(listing: listing).padding(14) }

                        HStack(alignment: .top, spacing: 12) {
                            Text(listing.title)
                                .font(.softHeading).tracking(-0.28)
                                .lineSpacing(-2)
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                            OTag(text: listing.listingType == "rent" ? "For rent" : "For sale", filled: true)
                                .padding(.top, 6)
                        }
                        .padding(.top, 22)

                        Text(ListingMeta.line(listing))
                            .font(.softSmall).foregroundStyle(Color.softSecondary)
                            .padding(.top, 8)

                        if listing.isStale {
                            Text("Not confirmed recently")
                                .font(.soft(14)).foregroundStyle(Color.softClay)
                                .padding(.top, 4)
                        }
                    }
                }
                .buttonStyle(SoftPressStyle())

                HStack(spacing: 12) {
                    SoftTile(systemImage: "tag", label: "Asking price",
                             value: Format.compactPrice(listing.price) + (listing.listingType == "rent" ? "/mo" : ""))
                    SoftTile(systemImage: "checkmark.shield", label: "Trust score", value: "\(listing.verificationScore)")
                }
                .padding(.top, 20)

                if isMine {
                    // Owners can't message or book themselves; send them to the listing's tools instead.
                    NavigationLink(value: listing.id) {
                        HStack(spacing: 10) {
                            OTag(text: "Your listing", filled: true)
                            Spacer()
                            Text("Manage").font(.soft(15, .regular))
                            Image(systemName: "chevron.right").font(.system(size: 13, weight: .light))
                        }
                        .foregroundStyle(Color.softText)
                        .padding(.horizontal, 6)
                    }
                    .buttonStyle(SoftPressStyle())
                    .padding(.top, 16)
                } else {
                    HStack(spacing: 10) {
                        PrimaryButton(title: "Message \(listing.owner?.name.split(separator: " ").first.map(String.init) ?? "poster")",
                                      action: onMessage)
                        SecondaryButton(title: "Book a viewing", tint: .softTile, action: onBook)
                    }
                    .padding(.top, 16)
                }
            }
        }
    }
}

/// Compact card: photo with badge, then title and price.
struct CompactListingCard: View {
    let listing: Listing

    var body: some View {
        SoftCard(padding: 12) {
            VStack(alignment: .leading, spacing: 0) {
                SoftPhoto(url: listing.images.first?.url)
                    .frame(height: 220)
                    .frame(maxWidth: .infinity)
                    .overlay(alignment: .topLeading) { PhotoTags(listing: listing).padding(12) }

                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    Text(listing.title)
                        .font(.softCardTitle)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    PriceText(price: listing.price, listingType: listing.listingType)
                }
                .padding(.horizontal, 8)
                .padding(.top, 14)
                .padding(.bottom, 6)

                if listing.isStale {
                    Text("Not confirmed recently")
                        .font(.soft(13)).foregroundStyle(Color.softClay)
                        .padding(.horizontal, 8).padding(.bottom, 4)
                }
            }
        }
    }
}

/// "₱22K" with a small "/mo" for rentals.
struct PriceText: View {
    let price: Int
    let listingType: String
    var size: CGFloat = 18

    var body: some View {
        (Text(Format.compactPrice(price)).font(.soft(size, .regular))
         + Text(listingType == "rent" ? "/mo" : "").font(.soft(size * 0.83)).foregroundColor(.softSecondary))
            .lineLimit(1)
    }
}

enum ListingMeta {
    /// "Muntinlupa, Metro Manila · 2 bd · 2 ba · 820 sqft"
    static func line(_ listing: Listing) -> String {
        let place = [listing.city, listing.state].filter { !$0.isEmpty }.joined(separator: ", ")
        var parts: [String] = []
        if !place.isEmpty { parts.append(place) }
        parts.append(Format.beds(listing.bedrooms))
        parts.append(Format.baths(listing.bathrooms))
        if listing.areaSqft > 0 { parts.append(Format.area(listing.areaSqft)) }
        return parts.joined(separator: " · ")
    }
}

/// Kept for screens that still use the plain list card (saved-search results, profile).
struct ListingCard: View {
    let listing: Listing
    var body: some View { CompactListingCard(listing: listing) }
}
