import SwiftUI

struct ListingsView: View {
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var query = ""
    @State private var listingType: String?
    @State private var propertyType: String?
    @State private var loading = false
    @State private var errorMessage: String?

    private let propertyTypes = ["house", "apartment", "condo", "townhouse", "land"]

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                filters

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
                    ContentUnavailableView(
                        "No listings found",
                        systemImage: "magnifyingglass",
                        description: Text("Try adjusting your search or filters.")
                    )
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
        .searchable(text: $query, prompt: "Search city, title, address")
        .onSubmit(of: .search) { Task { await load() } }
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private var filters: some View {
        VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip("All", selected: listingType == nil) { setListingType(nil) }
                    chip("Buy", selected: listingType == "sale") { setListingType("sale") }
                    chip("Rent", selected: listingType == "rent") { setListingType("rent") }
                }
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chip("Any type", selected: propertyType == nil) { setPropertyType(nil) }
                    ForEach(propertyTypes, id: \.self) { type in
                        chip(Format.capitalized(type), selected: propertyType == type) {
                            setPropertyType(type)
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

    private func setListingType(_ value: String?) {
        listingType = value
        Task { await load() }
    }

    private func setPropertyType(_ value: String?) {
        propertyType = value
        Task { await load() }
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            let response = try await appState.api.listings(
                query: query,
                propertyType: propertyType,
                listingType: listingType
            )
            listings = response.listings
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
            }
        }
    }
}
