import SwiftUI
import PhotosUI
import UIKit
import MapKit

/// One form for both posting and editing, so the two can never drift apart.
///
/// Editing exists because screening tells owners *why* a listing was flagged —
/// and without a way to act on that, the whole verification loop is a dead end.
struct ListingFormView: View {
    enum Mode: Equatable {
        case create
        case edit(Listing)

        var isEdit: Bool { if case .edit = self { return true }; return false }
    }

    let mode: Mode
    /// Called with the saved listing so the caller can navigate or refresh.
    var onSaved: (Listing) -> Void

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var description = ""
    @State private var price = ""
    @State private var listingType = "sale"
    @State private var propertyType = "house"
    @State private var bedrooms = ""
    @State private var bathrooms = ""
    @State private var area = ""
    @State private var address = ""
    @State private var city = ""
    @State private var state = ""
    @State private var zip = ""
    @State private var coordinate: CLLocationCoordinate2D?

    // Photos already on the listing (edit mode), and newly picked ones.
    @State private var existingImages: [ListingImage] = []
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var newImages: [Data] = []

    @State private var loading = false
    @State private var errorMessage: String?
    @State private var showDeleteConfirm = false
    @State private var showMapPicker = false

    private let propertyTypes = ["house", "apartment", "condo", "townhouse", "land"]

    private var canSubmit: Bool { !title.isEmpty && Int(price) != nil }

    private var editing: Listing? {
        if case .edit(let listing) = mode { return listing }
        return nil
    }

    var body: some View {
        Form {
            Section("Photos") {
                photoRow
                if editing != nil && !existingImages.isEmpty {
                    Text("The first photo is what buyers see on the card. Swipe to delete, or use the arrow to promote one.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            Section("Listing") {
                Picker("Type", selection: $listingType) {
                    Text("For sale").tag("sale")
                    Text("For rent").tag("rent")
                }
                .pickerStyle(.segmented)

                Picker("Property", selection: $propertyType) {
                    ForEach(propertyTypes, id: \.self) { Text(Format.capitalized($0)).tag($0) }
                }

                TextField("Title", text: $title)
                TextField("Price (₱)", text: $price).keyboardType(.numberPad)
                TextField("Description", text: $description, axis: .vertical).lineLimit(3...6)
            }

            Section("Details") {
                TextField("Bedrooms", text: $bedrooms).keyboardType(.numberPad)
                TextField("Bathrooms", text: $bathrooms).keyboardType(.decimalPad)
                TextField("Area (sq ft)", text: $area).keyboardType(.numberPad)
            }

            Section("Location") {
                TextField("Address", text: $address)
                TextField("City / Municipality", text: $city)
                TextField("Province", text: $state)
                TextField("Postal code", text: $zip).keyboardType(.numberPad)

                // Without a pin the listing never appears in map search.
                Button {
                    showMapPicker = true
                } label: {
                    HStack {
                        Label(coordinate == nil ? "Set map location" : "Map location set",
                              systemImage: coordinate == nil ? "mappin.slash" : "mappin.circle.fill")
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                    }
                }
                .foregroundStyle(coordinate == nil ? Color.cabinClay : Color.cabinForest)

                if coordinate == nil {
                    Text("Listings without a map pin don't show up in map search.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            Section("Before you publish") {
                qualityChecklist
                Text("Every listing is screened before it gets a verified badge. Editing sends it back for a fresh check.")
                    .font(.caption).foregroundStyle(.secondary)
            }

            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            }

            Section {
                Button(action: submit) {
                    if loading {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text(mode.isEdit ? "Save changes" : "Publish listing").frame(maxWidth: .infinity)
                    }
                }
                .disabled(!canSubmit || loading)
            }

            if mode.isEdit {
                Section {
                    Button(role: .destructive) { showDeleteConfirm = true } label: {
                        Text("Delete listing").frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .navigationTitle(mode.isEdit ? "Edit listing" : "Post a listing")
        .navigationBarTitleDisplayMode(mode.isEdit ? .inline : .large)
        .sheet(isPresented: $showMapPicker) {
            MapLocationPicker(coordinate: $coordinate, city: city)
        }
        .confirmationDialog("Delete this listing?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await deleteListing() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This can't be undone. Anyone you've been messaging about it will lose the thread.")
        }
        .onChange(of: pickerItems) { _, items in
            Task { await loadImages(items) }
        }
        .onAppear(perform: populate)
    }

    // MARK: Photos

    private var photoRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                PhotosPicker(selection: $pickerItems, maxSelectionCount: 6, matching: .images) {
                    VStack(spacing: 4) {
                        Image(systemName: "camera")
                        Text("Add").font(.caption)
                    }
                    .frame(width: 84, height: 84)
                    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
                }

                // Already-saved photos, each removable and promotable.
                ForEach(existingImages) { image in
                    ZStack(alignment: .topTrailing) {
                        RemoteImage(url: image.url)
                            .frame(width: 84, height: 84)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        Menu {
                            if existingImages.first?.id != image.id {
                                Button {
                                    Task { await promote(image) }
                                } label: { Label("Make thumbnail", systemImage: "arrow.up.square") }
                            }
                            Button(role: .destructive) {
                                Task { await removePhoto(image) }
                            } label: { Label("Delete photo", systemImage: "trash") }
                        } label: {
                            Image(systemName: "ellipsis.circle.fill")
                                .foregroundStyle(.white, .black.opacity(0.5))
                                .padding(4)
                        }
                    }
                }

                ForEach(Array(newImages.enumerated()), id: \.offset) { _, data in
                    if let uiImage = UIImage(data: data) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 84, height: 84)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }

    /// Live feedback on the things the reviewer actually weighs.
    private var qualityChecklist: some View {
        VStack(alignment: .leading, spacing: 6) {
            check("At least 3 photos", done: existingImages.count + newImages.count >= 3)
            check("Description of 20+ words", done: description.split(separator: " ").count >= 20)
            check("City or address filled in", done: !city.trimmingCharacters(in: .whitespaces).isEmpty
                                                  || !address.trimmingCharacters(in: .whitespaces).isEmpty)
            check("Price set", done: (Int(price) ?? 0) > 0)
            check("Map pin set", done: coordinate != nil)
            if propertyType != "land" {
                check("Bedrooms or area given", done: (Int(bedrooms) ?? 0) > 0 || (Int(area) ?? 0) > 0)
            }
        }
    }

    private func check(_ label: String, done: Bool) -> some View {
        Label(label, systemImage: done ? "checkmark.circle.fill" : "circle")
            .font(.caption)
            .foregroundStyle(done ? Color.cabinForest : .secondary)
    }

    // MARK: Actions

    private func populate() {
        guard let listing = editing, title.isEmpty else { return }
        title = listing.title
        description = listing.description
        price = String(listing.price)
        listingType = listing.listingType
        propertyType = listing.propertyType
        bedrooms = String(listing.bedrooms)
        bathrooms = String(listing.bathrooms)
        area = String(listing.areaSqft)
        address = listing.address
        city = listing.city
        state = listing.state
        zip = listing.zipCode
        existingImages = listing.images
        if let lat = listing.latitude, let lng = listing.longitude {
            coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
    }

    private func loadImages(_ items: [PhotosPickerItem]) async {
        var result: [Data] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                result.append(data)
            }
        }
        newImages = result
    }

    private func promote(_ image: ListingImage) async {
        guard let listing = editing else { return }
        var order = existingImages.map(\.id)
        order.removeAll { $0 == image.id }
        order.insert(image.id, at: 0)
        do {
            let updated = try await appState.api.reorderImages(listingId: listing.id, imageIds: order)
            existingImages = updated.images
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func removePhoto(_ image: ListingImage) async {
        guard let listing = editing else { return }
        do {
            let updated = try await appState.api.deleteImage(listingId: listing.id, imageId: image.id)
            existingImages = updated.images
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deleteListing() async {
        guard let listing = editing else { return }
        loading = true
        do {
            try await appState.api.deleteListing(id: listing.id)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    private func submit() {
        guard !loading else { return }
        loading = true
        errorMessage = nil

        let request = ListingRequest(
            title: title.trimmingCharacters(in: .whitespaces),
            description: description.trimmingCharacters(in: .whitespaces),
            price: Int(price) ?? 0,
            propertyType: propertyType,
            listingType: listingType,
            bedrooms: Int(bedrooms) ?? 0,
            bathrooms: Double(bathrooms) ?? 0,
            areaSqft: Int(area) ?? 0,
            address: address.trimmingCharacters(in: .whitespaces),
            city: city.trimmingCharacters(in: .whitespaces),
            state: state.trimmingCharacters(in: .whitespaces),
            zipCode: zip.trimmingCharacters(in: .whitespaces),
            latitude: coordinate?.latitude,
            longitude: coordinate?.longitude
        )
        let payloadImages = newImages

        Task {
            do {
                let listing: Listing
                if let existing = editing {
                    listing = try await appState.api.updateListing(id: existing.id, request)
                } else {
                    listing = try await appState.api.createListing(request)
                }
                for (index, data) in payloadImages.enumerated() {
                    _ = try? await appState.api.uploadImage(
                        listingId: listing.id,
                        data: data,
                        filename: "photo_\(index).jpg",
                        mime: "image/jpeg"
                    )
                }
                loading = false
                let saved = (try? await appState.api.listing(id: listing.id)) ?? listing
                if mode.isEdit {
                    onSaved(saved)
                    dismiss()
                } else {
                    resetForm()
                    onSaved(saved)
                }
            } catch {
                errorMessage = error.localizedDescription
                loading = false
            }
        }
    }

    private func resetForm() {
        title = ""; description = ""; price = ""
        bedrooms = ""; bathrooms = ""; area = ""
        address = ""; city = ""; state = ""; zip = ""
        coordinate = nil
        pickerItems = []; newImages = []
    }
}

/// Drops a pin by panning the map under a fixed marker — no location permission
/// needed, which keeps posting friction low.
struct MapLocationPicker: View {
    @Binding var coordinate: CLLocationCoordinate2D?
    let city: String

    @Environment(\.dismiss) private var dismiss
    @State private var camera: MapCameraPosition = .automatic
    @State private var centre: CLLocationCoordinate2D?

    var body: some View {
        NavigationStack {
            ZStack {
                Map(position: $camera)
                    .onMapCameraChange(frequency: .continuous) { context in
                        centre = context.region.center
                    }
                // A fixed crosshair: whatever is under it is the pin.
                Image(systemName: "mappin")
                    .font(.largeTitle)
                    .foregroundStyle(Color.cabinForest)
                    .shadow(radius: 3)
                    .offset(y: -12)
                    .allowsHitTesting(false)
            }
            .overlay(alignment: .bottom) {
                Text("Pan the map so the pin sits on the property.")
                    .font(.caption)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(.regularMaterial, in: Capsule())
                    .padding(.bottom, 16)
            }
            .navigationTitle("Set location")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Use this spot") {
                        if let centre { coordinate = centre }
                        dismiss()
                    }
                }
            }
            .task { positionCamera() }
        }
    }

    private func positionCamera() {
        // Start from the existing pin, else Metro Manila south, where most of
        // the surveyed users live.
        let start = coordinate ?? CLLocationCoordinate2D(latitude: 14.4223, longitude: 121.0292)
        camera = .region(MKCoordinateRegion(
            center: start,
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        ))
        centre = start
    }
}

// MARK: - Thin wrappers

struct CreateListingView: View {
    @State private var createdListingId: String?

    var body: some View {
        ListingFormView(mode: .create) { listing in
            createdListingId = listing.id
        }
        .navigationDestination(item: $createdListingId) { id in
            ListingDetailView(listingId: id)
        }
    }
}

struct EditListingView: View {
    let listing: Listing
    var onSaved: (Listing) -> Void

    var body: some View {
        ListingFormView(mode: .edit(listing), onSaved: onSaved)
    }
}
