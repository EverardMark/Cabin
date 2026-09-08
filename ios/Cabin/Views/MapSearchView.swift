import SwiftUI
import MapKit

/// Map-based property search — 48% of respondents asked for it. Listings load
/// for whatever region is on screen, so panning is the search. Pins are photo
/// circles; the selected one gets a black ring and its price and trust score
/// appear in the floating pills.
struct MapSearchView: View {
    @State var filters = ListingFilters()

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

    private var verifiedInView: Int {
        pinned.filter { $0.verificationStatus == .verified }.count
    }

    var body: some View {
        Map(position: $camera) {
            ForEach(pinned) { listing in
                let coordinate = CLLocationCoordinate2D(latitude: listing.latitude ?? 0, longitude: listing.longitude ?? 0)
                Annotation("", coordinate: coordinate, anchor: .bottom) {
                    MapPin(listing: listing, selected: selected?.id == listing.id)
                        .onTapGesture {
                            withAnimation(.snappy) { selected = listing }
                        }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .mapControls { }
        .onMapCameraChange(frequency: .onEnd) { context in
            visibleRegion = context.region
            Task { await load(context.region) }
        }
        .overlay(alignment: .top) { searchBar }
        .overlay(alignment: .bottomLeading) { pills }
        .softScreen()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .task {
            if let region = visibleRegion { await load(region) }
        }
    }

    // MARK: - Overlays

    private var searchBar: some View {
        HStack(spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass").font(.system(size: 18, weight: .light)).foregroundStyle(Color.softLabel)
                TextField("", text: $filters.query,
                          prompt: Text("Search").font(.soft(18)).foregroundStyle(Color.softLabel))
                    .font(.soft(18))
                    .submitLabel(.search)
                    .onSubmit { if let region = visibleRegion { Task { await load(region) } } }
                if loading {
                    ProgressView().tint(Color.softInk)
                }
            }
            .padding(.horizontal, 18)
            .frame(height: 52)
            .background(Color.white, in: Capsule())
            .softShadow(.row)

            Menu {
                Toggle("Verified only", isOn: $filters.verifiedOnly)
                Toggle("Recently confirmed", isOn: $filters.excludeStale)
                Picker("Type", selection: $filters.listingType) {
                    Text("Buy or rent").tag(String?.none)
                    Text("Buy").tag(String?.some("sale"))
                    Text("Rent").tag(String?.some("rent"))
                }
            } label: {
                ZStack {
                    Circle().fill(Color.white)
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 20, weight: .light))
                        .foregroundStyle(Color.softTextSoft)
                }
                .frame(width: 52, height: 52)
                .softShadow(.circle)
            }
            .onChange(of: filters) { if let region = visibleRegion { Task { await load(region) } } }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var pills: some View {
        VStack(alignment: .leading, spacing: 18) {
            SoftPill(value: "\(verifiedInView)", label: "Verified in view")
            if let selected {
                NavigationLink(value: selected.id) {
                    SoftPill(value: Format.compactPrice(selected.price) + (selected.listingType == "rent" ? "/mo" : ""),
                             label: selected.title)
                }
                .buttonStyle(SoftPressStyle())
                SoftPill(value: "\(selected.verificationScore)",
                         label: selected.verificationStatus == .verified ? "Trust score · screened" : "Trust score · \(selected.verificationStatus.label.lowercased())")
            } else if let errorMessage {
                Text(errorMessage)
                    .font(.soft(13)).foregroundStyle(Color.softText)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Color.white.opacity(0.9), in: Capsule())
            } else if !pinned.isEmpty {
                Text("Tap a pin")
                    .font(.soft(13)).foregroundStyle(Color.softText)
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Color.white.opacity(0.9), in: Capsule())
            }
        }
        .frame(maxWidth: 220, alignment: .leading)
        .padding(.leading, 24)
        .padding(.bottom, 110)
        .animation(.snappy, value: selected?.id)
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
            if let selected, !listings.contains(where: { $0.id == selected.id }) {
                self.selected = nil
            }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

/// Photo circle with a white (or black, when selected) ring and a dot beneath (.pin).
struct MapPin: View {
    let listing: Listing
    let selected: Bool

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                RemoteImage(url: listing.images.first?.url)
                    .frame(width: 56, height: 56)
                    .clipShape(Circle())
                Circle().strokeBorder(selected ? Color.softInk : Color.white, lineWidth: 3)
            }
            .frame(width: 56, height: 56)
            .shadow(color: .black.opacity(0.2), radius: 8, y: 6)
            Circle().fill(Color.softInk).frame(width: 6, height: 6)
        }
        .scaleEffect(selected ? 1.08 : 1)
    }
}
