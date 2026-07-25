import SwiftUI

struct ListingDetailView: View {
    let listingId: String
    @Environment(AppState.self) private var appState

    @State private var listing: Listing?
    @State private var reviews: [Review] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showContact = false
    @State private var showReview = false
    @State private var showReport = false

    private var isOwner: Bool { listing?.userId == appState.currentUser?.id }

    var body: some View {
        Group {
            if loading {
                ProgressView()
            } else if let listing {
                content(listing)
            } else {
                ContentUnavailableView(
                    "Couldn't load listing",
                    systemImage: "exclamationmark.triangle",
                    description: Text(errorMessage ?? "")
                )
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .sheet(isPresented: $showReview) {
            if let owner = listing?.owner {
                WriteReviewSheet(ownerName: owner.name) { rating, comment in
                    await submitReview(ownerId: owner.id, rating: rating, comment: comment)
                }
            }
        }
        .sheet(isPresented: $showReport) {
            if let listing {
                ReportSheet(listingTitle: listing.title) { reason, detail in
                    await submitReport(listingId: listing.id, reason: reason, detail: detail)
                }
            }
        }
    }

    @ViewBuilder
    private func content(_ listing: Listing) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                gallery(listing)

                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Pill(text: listing.listingType == "rent" ? "For rent" : "For sale")
                        Pill(
                            text: Format.capitalized(listing.propertyType),
                            background: Color(.secondarySystemBackground),
                            foreground: .primary
                        )
                        if listing.status.lowercased() != "active" {
                            StatusPill(status: listing.status)
                        }
                    }

                    Text(Format.price(listing.price, listingType: listing.listingType))
                        .font(.title.bold())
                        .foregroundStyle(Color.cabinForest)

                    Text(listing.title).font(.title3.weight(.semibold))

                    let address = fullAddress(listing)
                    if !address.isEmpty {
                        Label(address, systemImage: "mappin.and.ellipse")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    if let rel = Format.relativeTime(listing.updatedAt) {
                        Label("Updated \(rel)", systemImage: "clock")
                            .font(.caption).foregroundStyle(.secondary)
                    }

                    featureCard(listing)

                    if !listing.description.isEmpty {
                        Text("About this property").font(.headline).padding(.top, 4)
                        Text(listing.description).foregroundStyle(.primary.opacity(0.85))
                    }

                    if let owner = listing.owner { ownerCard(owner) }

                    reviewsSection(listing)

                    if isOwner {
                        availabilitySection(listing)
                    } else {
                        Button {
                            showContact = true
                        } label: {
                            Text("Contact agent").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                        .padding(.top, 4)

                        Button(role: .destructive) {
                            showReport = true
                        } label: {
                            Label("Report listing", systemImage: "flag").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                }
                .padding(16)
            }
        }
        .alert("Contact agent", isPresented: $showContact) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("This is where you'd reach out to \(listing.owner?.name ?? "the agent").")
        }
    }

    private func gallery(_ listing: Listing) -> some View {
        Group {
            if listing.images.isEmpty {
                RemoteImage(url: nil).frame(height: 280).frame(maxWidth: .infinity)
            } else {
                TabView {
                    ForEach(listing.images) { image in
                        RemoteImage(url: image.url).frame(maxWidth: .infinity)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: listing.images.count > 1 ? .automatic : .never))
                .frame(height: 280)
            }
        }
    }

    private func featureCard(_ listing: Listing) -> some View {
        HStack {
            FeatureStat(value: Format.beds(listing.bedrooms), label: "Bedrooms")
            Divider().frame(height: 34)
            FeatureStat(value: Format.baths(listing.bathrooms), label: "Bathrooms")
            Divider().frame(height: 34)
            FeatureStat(value: Format.area(listing.areaSqft), label: "Area")
        }
        .padding(.vertical, 14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func ownerCard(_ owner: UserSummary) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.cabinForest).frame(width: 44, height: 44)
                Text(owner.name.first.map { String($0).uppercased() } ?? "?")
                    .font(.headline).foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Listed by").font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    Text(owner.name).font(.headline)
                    if owner.verified { VerifiedBadge() }
                }
                RatingStars(avg: owner.ratingAvg, count: owner.ratingCount)
            }
            Spacer()
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func reviewsSection(_ listing: Listing) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Reviews").font(.headline)

            if reviews.isEmpty {
                Text("No reviews yet.").font(.subheadline).foregroundStyle(.secondary)
            } else {
                ForEach(reviews.prefix(5)) { review in
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(review.authorName).font(.subheadline.weight(.semibold))
                            Spacer()
                            HStack(spacing: 1) {
                                ForEach(0..<max(0, review.rating), id: \.self) { _ in
                                    Image(systemName: "star.fill").font(.caption2).foregroundStyle(.orange)
                                }
                            }
                        }
                        if !review.comment.isEmpty {
                            Text(review.comment).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                    Divider()
                }
            }

            if !isOwner {
                Button {
                    showReview = true
                } label: {
                    Label("Write a review", systemImage: "square.and.pencil")
                }
                .font(.subheadline)
            }
        }
    }

    private func availabilitySection(_ listing: Listing) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Manage availability").font(.headline)
            Text("Keep your listing honest — mark it Sold or Rented when it's gone so buyers don't chase ghost listings.")
                .font(.caption).foregroundStyle(.secondary)
            Menu {
                ForEach(["active", "pending", "sold", "rented", "inactive"], id: \.self) { status in
                    Button(Format.statusLabel(status)) { Task { await changeStatus(status) } }
                }
            } label: {
                HStack {
                    Text("Status: \(Format.statusLabel(listing.status))")
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down")
                }
                .padding(12)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(.top, 4)
    }

    private func fullAddress(_ listing: Listing) -> String {
        [listing.address, listing.city, listing.state, listing.zipCode]
            .filter { !$0.isEmpty }.joined(separator: ", ")
    }

    private func load() async {
        if listing == nil { loading = true }
        do {
            let loaded = try await appState.api.listing(id: listingId)
            listing = loaded
            if let ownerId = loaded.owner?.id {
                reviews = (try? await appState.api.reviews(userId: ownerId).reviews) ?? []
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    private func submitReview(ownerId: String, rating: Int, comment: String) async -> String? {
        do {
            try await appState.api.addReview(userId: ownerId, rating: rating, comment: comment)
            await load()
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private func submitReport(listingId: String, reason: String, detail: String) async -> String? {
        do {
            try await appState.api.report(listingId: listingId, reason: reason, detail: detail)
            return nil
        } catch {
            return error.localizedDescription
        }
    }

    private func changeStatus(_ status: String) async {
        do {
            _ = try await appState.api.updateListingStatus(id: listingId, status: status)
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

// MARK: - Sheets

struct WriteReviewSheet: View {
    let ownerName: String
    let onSubmit: (Int, String) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var rating = 5
    @State private var comment = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Your rating") {
                    Picker("Rating", selection: $rating) {
                        ForEach(1...5, id: \.self) { Text("\($0) ★").tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Comment") {
                    TextField("Share your experience", text: $comment, axis: .vertical).lineLimit(3...6)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Review \(ownerName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") {
                        Task {
                            busy = true
                            error = await onSubmit(rating, comment)
                            busy = false
                            if error == nil { dismiss() }
                        }
                    }
                    .disabled(busy)
                }
            }
        }
    }
}

struct ReportSheet: View {
    let listingTitle: String
    let onSubmit: (String, String) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var reason = "scam"
    @State private var detail = ""
    @State private var busy = false
    @State private var error: String?

    private let reasons: [(String, String)] = [
        ("scam", "Scam"),
        ("fake_or_misleading", "Fake or misleading"),
        ("already_unavailable", "Already unavailable"),
        ("wrong_price", "Wrong price"),
        ("duplicate", "Duplicate"),
        ("offensive", "Offensive"),
        ("other", "Other"),
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("Reason") {
                    Picker("Reason", selection: $reason) {
                        ForEach(reasons, id: \.0) { Text($0.1).tag($0.0) }
                    }
                }
                Section("Details (optional)") {
                    TextField("What's wrong with this listing?", text: $detail, axis: .vertical).lineLimit(3...6)
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Report listing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") {
                        Task {
                            busy = true
                            error = await onSubmit(reason, detail)
                            busy = false
                            if error == nil { dismiss() }
                        }
                    }
                    .disabled(busy)
                }
            }
        }
    }
}
