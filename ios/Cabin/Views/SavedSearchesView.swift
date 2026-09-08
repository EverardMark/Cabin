import SwiftUI

/// Saved searches with a new-match count — the "saved searches and instant
/// alerts" ask from the survey's free-text answers.
struct SavedSearchesView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var searches: [SavedSearch] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                CircleButton(systemImage: "chevron.left") { dismiss() }
            } title: {
                Text("Saved searches").font(.softScreenTitle)
            } trailing: {
                Color.clear.frame(width: 48, height: 48)
            }

            ScrollView {
                LazyVStack(spacing: 12) {
                    if loading && searches.isEmpty {
                        ProgressView().tint(Color.softInk).padding(.top, 40)
                    } else if searches.isEmpty {
                        SoftEmpty(systemImage: "bell.slash", title: "No saved searches",
                                  message: errorMessage ?? "Open the search panel on Browse, set your filters, then tap “Save this search”.")
                    } else {
                        ForEach(searches) { search in
                            NavigationLink(value: search) {
                                SoftRow {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(search.name).font(.softBody).lineLimit(1)
                                        Text(readable(search.query))
                                            .font(.soft(14)).foregroundStyle(Color.softSecondary).lineLimit(2)
                                    }
                                    .padding(.leading, 8)
                                    Spacer(minLength: 4)
                                    if search.newMatches > 0 {
                                        VTag(text: "\(search.newMatches) new", systemImage: nil, tint: .softAccent)
                                    } else {
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 14, weight: .light))
                                            .foregroundStyle(Color.softMuted)
                                            .padding(.trailing, 4)
                                    }
                                }
                            }
                            .buttonStyle(SoftPressStyle())
                            .contextMenu {
                                Button(role: .destructive) {
                                    Task { await delete(search) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                        Text("Press and hold a search to delete it.")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                            .padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 48)
            }
        }
        .softScreen()
        .hidesSoftTabBar()
        .navigationDestination(for: SavedSearch.self) { search in
            SavedSearchResultsView(search: search)
        }
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

    private func delete(_ search: SavedSearch) async {
        try? await appState.api.deleteSavedSearch(id: search.id)
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
    @Environment(\.dismiss) private var dismiss

    @State private var listings: [Listing] = []
    @State private var loading = true
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                CircleButton(systemImage: "chevron.left") { dismiss() }
            } title: {
                Text(search.name).font(.softScreenTitle).lineLimit(1).padding(.horizontal, 60)
            } trailing: {
                Color.clear.frame(width: 48, height: 48)
            }

            ScrollView {
                LazyVStack(spacing: 16) {
                    if loading {
                        ProgressView().tint(Color.softInk).padding(.top, 40)
                    } else if listings.isEmpty {
                        SoftEmpty(systemImage: "magnifyingglass", title: "Nothing matches right now",
                                  message: errorMessage ?? "We'll keep counting new matches for you.")
                    } else {
                        ForEach(listings) { listing in
                            NavigationLink(value: listing.id) {
                                CompactListingCard(listing: listing)
                            }
                            .buttonStyle(SoftPressStyle())
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 48)
            }
        }
        .softScreen()
        .hidesSoftTabBar()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
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
