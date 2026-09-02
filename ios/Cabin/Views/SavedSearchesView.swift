import SwiftUI

/// Saved searches with a new-match count — the "saved searches and instant
/// alerts" ask from the survey's free-text answers.
struct SavedSearchesView: View {
    @Environment(AppState.self) private var appState

    @State private var searches: [SavedSearch] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if loading && searches.isEmpty {
                ProgressView()
            } else if searches.isEmpty {
                ContentUnavailableView {
                    Label("No saved searches", systemImage: "bell.slash")
                } description: {
                    Text(errorMessage ?? "Set up filters when browsing, then tap the filter menu and choose “Save this search”.")
                }
            } else {
                List {
                    ForEach(searches) { search in
                        NavigationLink {
                            SavedSearchResultsView(search: search)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(search.name).font(.subheadline.weight(.semibold))
                                    Text(readable(search.query))
                                        .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                                }
                                Spacer()
                                if search.newMatches > 0 {
                                    Text("\(search.newMatches) new")
                                        .font(.caption2.weight(.bold))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 7).padding(.vertical, 3)
                                        .background(Color.cabinForest, in: Capsule())
                                }
                            }
                        }
                    }
                    .onDelete { offsets in
                        Task { await delete(offsets) }
                    }
                }
            }
        }
        .navigationTitle("Saved searches")
        .task { await load() }
        .refreshable { await load() }
    }

    /// Renders "city=Muntinlupa&verified_only=true" as something a person reads.
    private func readable(_ query: String) -> String {
        guard let items = URLComponents(string: "?" + query)?.queryItems else { return query }
        return items.compactMap { item -> String? in
            switch item.name {
            case "q":             return "“\(item.value ?? "")”"
            case "city":          return item.value
            case "listing_type":  return item.value == "rent" ? "for rent" : "for sale"
            case "property_type": return item.value.map { Format.capitalized($0) }
            case "min_price":     return item.value.flatMap { Int($0) }.map { "from \(Format.compactPrice($0))" }
            case "max_price":     return item.value.flatMap { Int($0) }.map { "up to \(Format.compactPrice($0))" }
            case "min_bedrooms":  return item.value.map { "\($0)+ beds" }
            case "verified_only": return item.value == "true" ? "verified only" : nil
            case "exclude_stale": return item.value == "true" ? "recently confirmed" : nil
            default:              return nil
            }
        }
        .joined(separator: " · ")
    }

    private func delete(_ offsets: IndexSet) async {
        for index in offsets {
            let search = searches[index]
            try? await appState.api.deleteSavedSearch(id: search.id)
        }
        await load()
    }

    private func load() async {
        loading = true
        do {
            searches = try await appState.api.savedSearches().searches
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

struct SavedSearchResultsView: View {
    let search: SavedSearch
    @Environment(AppState.self) private var appState

    @State private var listings: [Listing] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                if loading {
                    ProgressView().padding(.top, 40)
                } else if listings.isEmpty {
                    ContentUnavailableView(
                        "Nothing matches right now",
                        systemImage: "magnifyingglass",
                        description: Text(errorMessage ?? "We'll keep counting new matches for you.")
                    )
                    .padding(.top, 40)
                } else {
                    ForEach(listings) { listing in
                        NavigationLink { ListingDetailView(listingId: listing.id) } label: {
                            ListingCard(listing: listing)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle(search.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        loading = true
        do {
            listings = try await appState.api.runSavedSearch(id: search.id).listings
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}
