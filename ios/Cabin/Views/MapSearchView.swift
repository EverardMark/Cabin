import SwiftUI
import MapKit

/// Map-based property search — 48% of respondents asked for it. Listings load
/// for whatever region is on screen, so panning is the search.
struct MapSearchView: View {
    let filters: ListingFilters

    @Environment(AppState.self) private var appState

    // Metro Manila south / Muntinlupa, where most survey respondents live.
    @State private var camera: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 14.4223, longitude: 121.0292),
            span: MKCoordinateSpan(latitudeDelta: 0.30, longitudeDelta: 0.30)
        )
    )
    @State private var listings: [Listing] = []
    @State private var selected: Listing?
    @State private var visibleRegion: MKCoordinateRegion?
    @State private var loading = false
    @State private var errorMessage: String?

    private var pinned: [Listing] {
        listings.filter { $0.latitude != nil && $0.longitude != nil }
    }

    var body: some View {
        Map(position: $camera, selection: Binding(
            get: { selected?.id },
            set: { id in selected = listings.first { $0.id == id } }
        )) {
            ForEach(pinned) { listing in
                let verified = listing.verificationStatus == .verified
                let label: String = Format.compactPrice(listing.price)
                let symbol: String = verified ? "checkmark.seal.fill" : "house.fill"
                let tint: Color = verified ? .cabinForest : .cabinClay
                let coordinate = CLLocationCoordinate2D(
                    latitude: listing.latitude ?? 0,
                    longitude: listing.longitude ?? 0
                )
                Marker(label, systemImage: symbol, coordinate: coordinate)
                    .tint(tint)
                    .tag(listing.id)
            }
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            visibleRegion = context.region
            Task { await load(context.region) }
        }
        .overlay(alignment: .top) {
            if loading {
                ProgressView()
                    .padding(8)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.top, 8)
            } else if let errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .padding(8)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.top, 8)
            }
        }
        .overlay(alignment: .bottom) {
            if let selected {
                NavigationLink { ListingDetailView(listingId: selected.id) } label: {
                    MapListingPreview(listing: selected)
                }
                .buttonStyle(.plain)
                .padding(12)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            } else if !pinned.isEmpty {
                Text("\(pinned.count) listing\(pinned.count == 1 ? "" : "s") in view — tap a pin")
                    .font(.caption)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, 12)
            }
        }
        .animation(.snappy, value: selected)
        .navigationTitle("Map")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if let region = visibleRegion { await load(region) }
        }
    }

    private func load(_ region: MKCoordinateRegion) async {
        loading = true
        let box = (
            minLat: region.center.latitude - region.span.latitudeDelta / 2,
            maxLat: region.center.latitude + region.span.latitudeDelta / 2,
            minLng: region.center.longitude - region.span.longitudeDelta / 2,
            maxLng: region.center.longitude + region.span.longitudeDelta / 2
        )
        do {
            listings = try await appState.api.listings(inBoundingBox: box, filters: filters).listings
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

struct MapListingPreview: View {
    let listing: Listing

    var body: some View {
        HStack(spacing: 12) {
            RemoteImage(url: listing.images.first?.url)
                .frame(width: 68, height: 68)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 3) {
                Text(listing.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text(Format.price(listing.price, listingType: listing.listingType))
                    .font(.subheadline).foregroundStyle(Color.cabinForest)
                HStack(spacing: 6) {
                    VerificationBadge(status: listing.verificationStatus)
                    Text(Format.beds(listing.bedrooms)).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
        }
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        .shadow(radius: 6, y: 2)
    }
}
