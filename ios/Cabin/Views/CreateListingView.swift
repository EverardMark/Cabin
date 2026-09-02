import SwiftUI
import PhotosUI
import UIKit

struct CreateListingView: View {
    @Environment(AppState.self) private var appState

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

    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var images: [Data] = []

    @State private var loading = false
    @State private var errorMessage: String?
    @State private var createdListingId: String?

    private let propertyTypes = ["house", "apartment", "condo", "townhouse", "land"]

    private var canSubmit: Bool { !title.isEmpty && Int(price) != nil }

    var body: some View {
        Form {
            Section("Photos") {
                photoRow
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
            }

            Section("Before you publish") {
                qualityChecklist
                Text("Every listing is screened before it gets a verified badge. Complete listings with real photos pass; thin ones get flagged for buyers to see.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            }

            Section {
                Button(action: submit) {
                    if loading {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("Publish listing").frame(maxWidth: .infinity)
                    }
                }
                .disabled(!canSubmit || loading)
            }
        }
        .navigationTitle("Post a listing")
        .navigationDestination(item: $createdListingId) { id in
            ListingDetailView(listingId: id)
        }
        .onChange(of: pickerItems) { _, items in
            Task { await loadImages(items) }
        }
    }

    /// Live feedback on the things the reviewer actually weighs.
    private var qualityChecklist: some View {
        VStack(alignment: .leading, spacing: 6) {
            check("At least 3 photos", done: images.count >= 3)
            check("Description of 20+ words", done: description.split(separator: " ").count >= 20)
            check("City or address filled in", done: !city.trimmingCharacters(in: .whitespaces).isEmpty
                                                  || !address.trimmingCharacters(in: .whitespaces).isEmpty)
            check("Price set", done: (Int(price) ?? 0) > 0)
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
                ForEach(Array(images.enumerated()), id: \.offset) { _, data in
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

    private func loadImages(_ items: [PhotosPickerItem]) async {
        var result: [Data] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                result.append(data)
            }
        }
        images = result
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
            zipCode: zip.trimmingCharacters(in: .whitespaces)
        )
        let payloadImages = images

        Task {
            do {
                let listing = try await appState.api.createListing(request)
                for (index, data) in payloadImages.enumerated() {
                    _ = try? await appState.api.uploadImage(
                        listingId: listing.id,
                        data: data,
                        filename: "photo_\(index).jpg",
                        mime: "image/jpeg"
                    )
                }
                loading = false
                resetForm()
                createdListingId = listing.id
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
        pickerItems = []; images = []
    }
}
