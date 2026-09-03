import SwiftUI

struct ListingsView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var filters = ListingFilters()
    @State private var loading = false
    @State private var errorMessage: String?
    @State private var showSaveSearch = false
    @State private var saveSearchName = ""
    @State private var saveConfirmation: String?

    private let propertyTypes = ["house", "apartment", "condo", "townhouse", "land"]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                trustFilter
                filterChips

                if loading && listings.isEmpty {
                    ProgressView().padding(.top, 40)
                } else if let errorMessage, listings.isEmpty {
                    ContentUnavailableView {
                        Label("Couldn't load listings", systemImage: "wifi.slash")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button("Retry") { Task { await load() } }
                    }
                    .padding(.top, 40)
                } else if listings.isEmpty {
                    ContentUnavailableView {
                        Label("No listings found", systemImage: "magnifyingglass")
                    } description: {
                        Text(filters.verifiedOnly
                             ? "Nothing verified matches these filters yet. Turn off “Verified only” to include listings still being screened."
                             : "Try adjusting your search or filters.")
                    } actions: {
                        if filters.verifiedOnly {
                            Button("Include unverified") {
                                filters.verifiedOnly = false
                                Task { await load() }
                            }
                        }
                    }
                    .padding(.top, 40)
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
        .navigationTitle("Find your place")
        .searchable(text: $filters.query, prompt: "Search city, title, address")
        .onSubmit(of: .search) { Task { await load() } }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink { MapSearchView(filters: filters) } label: {
                    Image(systemName: "map")
                }
                .accessibilityLabel("Map view")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("Sort", selection: $filters.sort) {
                        Text("Newest").tag("recent")
                        Text("Most trusted").tag("trusted")
                        Text("Price: low to high").tag("price_asc")
                        Text("Price: high to low").tag("price_desc")
                    }
                    Toggle("Hide unconfirmed listings", isOn: $filters.excludeStale)
                    Divider()
                    Button {
                        saveSearchName = defaultSearchName
                        showSaveSearch = true
                    } label: {
                        Label("Save this search", systemImage: "bell.badge")
                    }
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                }
                .onChange(of: filters.sort) { Task { await load() } }
                .onChange(of: filters.excludeStale) { Task { await load() } }
            }
        }
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
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
        .task { await load() }
        .refreshable { await load() }
    }

    /// The verified-only switch sits above everything else: it is the single
    /// most requested feature in the survey (79%), and it defaults to on.
    private var trustFilter: some View {
        Toggle(isOn: $filters.verifiedOnly) {
            HStack(spacing: 6) {
                Image(systemName: "checkmark.seal.fill").foregroundStyle(Color.cabinForest)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Verified listings only").font(.subheadline.weight(.semibold))
                    Text("Hides listings that haven't passed screening")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .tint(.cabinForest)
        .padding(12)
        .background(Color.cabinForest.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        .onChange(of: filters.verifiedOnly) { Task { await load() } }
    }

    private var filterChips: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip("All", selected: filters.listingType == nil) { set(\.listingType, nil) }
                    chip("Buy", selected: filters.listingType == "sale") { set(\.listingType, "sale") }
                    chip("Rent", selected: filters.listingType == "rent") { set(\.listingType, "rent") }
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip("Any type", selected: filters.propertyType == nil) { set(\.propertyType, nil) }
                    ForEach(propertyTypes, id: \.self) { type in
                        chip(Format.capitalized(type), selected: filters.propertyType == type) {
                            set(\.propertyType, type)
                        }
                    }
                }
            }
        }
    }

    private func chip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Color.cabinForest : Color(.secondarySystemBackground), in: Capsule())
                .foregroundStyle(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
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

struct ListingCard: View {
    let listing: Listing

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            RemoteImage(url: listing.images.first?.url)
                .frame(height: 200)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(alignment: .topLeading) {
                    // The trust badge leads, because trust is what buyers said
                    // they decide on first; promotion sits below it, never above.
                    VStack(alignment: .leading, spacing: 4) {
                        VerificationBadge(status: listing.verificationStatus)
                            .background(Capsule().fill(Color(.systemBackground).opacity(0.92)).padding(-4))
                        if listing.isFeatured {
                            FeaturedBadge()
                                .background(Capsule().fill(Color(.systemBackground).opacity(0.92)).padding(-4))
                        }
                    }
                    .padding(12)
                }
                .overlay(alignment: .topTrailing) {
                    Pill(text: listing.listingType == "rent" ? "For rent" : "For sale")
                        .padding(10)
                }
                .overlay(alignment: .bottomLeading) {
                    Pill(
                        text: Format.price(listing.price, listingType: listing.listingType),
                        background: Color(.systemBackground),
                        foreground: .cabinForest
                    )
                    .padding(10)
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(listing.title).font(.headline).lineLimit(1)
                Text([listing.city, listing.state].filter { !$0.isEmpty }.joined(separator: ", "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    Text(Format.beds(listing.bedrooms))
                    Text("•").foregroundStyle(.secondary)
                    Text(Format.baths(listing.bathrooms))
                    Text("•").foregroundStyle(.secondary)
                    Text(Format.area(listing.areaSqft))
                }
                .font(.subheadline.weight(.medium))
                .padding(.top, 2)

                if listing.isStale {
                    Label("Not confirmed recently", systemImage: "clock.badge.exclamationmark")
                        .font(.caption)
                        .foregroundStyle(Color.cabinClay)
                        .padding(.top, 2)
                }
                if let owner = listing.owner {
                    HStack(spacing: 6) {
                        Text(owner.name).font(.caption).foregroundStyle(.secondary)
                        if owner.isVerified {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.caption2).foregroundStyle(Color.cabinForest)
                        }
                        RatingStars(rating: owner.ratingAvg, count: owner.ratingCount)
                    }
                    .padding(.top, 2)
                }
            }
        }
    }
}
