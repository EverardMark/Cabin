import SwiftUI

struct ListingDetailView: View {
    let listingId: String
    @Environment(AppState.self) private var appState

    @State private var listing: Listing?
    @State private var comparison: PriceComparison?
    @State private var loading = true
    @State private var errorMessage: String?

    @State private var showReport = false
    @State private var showBooking = false
    @State private var openedConversation: Conversation?
    @State private var actionError: String?
    @State private var confirmingAvailability = false

    private var isMine: Bool { listing?.userId == appState.currentUser?.id }

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
    }

    @ViewBuilder
    private func content(_ listing: Listing) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                gallery(listing)

                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Pill(text: listing.listingType == "rent" ? "For rent" : "For sale")
                        Pill(
                            text: Format.capitalized(listing.propertyType),
                            background: Color(.secondarySystemBackground),
                            foreground: .primary
                        )
                    }

                    Text(Format.price(listing.price, listingType: listing.listingType))
                        .font(.title.bold())
                        .foregroundStyle(Color.cabinForest)

                    Text(listing.title).font(.title3.weight(.semibold))

                    let address = fullAddress(listing)
                    if !address.isEmpty {
                        Label(address, systemImage: "mappin.and.ellipse")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    // Trust comes before the sales copy.
                    TrustPanel(listing: listing)
                    if listing.isStale { StaleWarning() }

                    featureCard(listing)

                    if let comparison, comparison.sampleSize >= 3 {
                        PriceComparisonCard(comparison: comparison)
                    }

                    if !listing.description.isEmpty {
                        Text("About this property").font(.headline).padding(.top, 4)
                        Text(listing.description)
                            .foregroundStyle(.primary.opacity(0.85))
                    }

                    if let owner = listing.owner {
                        Text("Listed by").font(.headline).padding(.top, 4)
                        NavigationLink {
                            UserProfileView(userId: owner.id)
                        } label: {
                            PosterCard(owner: owner, onViewProfile: {})
                                .allowsHitTesting(false)
                        }
                        .buttonStyle(.plain)
                    }

                    if isMine {
                        ownerActions(listing)
                    } else {
                        buyerActions(listing)
                    }
                }
                .padding(16)
            }
        }
        .navigationDestination(item: $openedConversation) { conv in
            ChatView(conversation: conv)
        }
        .sheet(isPresented: $showReport) {
            ReportListingSheet(listingId: listing.id) { await load() }
        }
        .sheet(isPresented: $showBooking) {
            BookViewingSheet(listing: listing)
        }
        .alert("Something went wrong", isPresented: .constant(actionError != nil)) {
            Button("OK") { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
    }

    /// What a buyer can do: message, book a viewing, or report the listing.
    @ViewBuilder
    private func buyerActions(_ listing: Listing) -> some View {
        VStack(spacing: 10) {
            Button {
                Task { await startChat(listing) }
            } label: {
                Label("Message the poster", systemImage: "bubble.left.and.bubble.right.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)

            Button {
                showBooking = true
            } label: {
                Label("Request a viewing", systemImage: "calendar.badge.plus")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .controlSize(.large)

            Button(role: .destructive) {
                showReport = true
            } label: {
                Label("Report this listing", systemImage: "flag")
                    .font(.subheadline)
            }
            .padding(.top, 2)
        }
        .padding(.top, 8)
    }

    /// What the owner sees instead: keep the listing fresh.
    @ViewBuilder
    private func ownerActions(_ listing: Listing) -> some View {
        VStack(spacing: 10) {
            if listing.isStale {
                Text("Buyers are shown a warning on listings that haven't been confirmed recently.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Button {
                Task { await confirmAvailability() }
            } label: {
                if confirmingAvailability {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Label("Confirm it's still available", systemImage: "checkmark.circle")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .disabled(confirmingAvailability)
        }
        .padding(.top, 8)
    }

    private func gallery(_ listing: Listing) -> some View {
        Group {
            if listing.images.isEmpty {
                ZStack {
                    RemoteImage(url: nil)
                    VStack(spacing: 6) {
                        Image(systemName: "photo.badge.exclamationmark").font(.largeTitle)
                        Text("No photos yet").font(.subheadline)
                    }
                    .foregroundStyle(.secondary)
                }
                .frame(height: 280)
                .frame(maxWidth: .infinity)
            } else {
                TabView {
                    ForEach(listing.images) { image in
                        RemoteImage(url: image.url)
                            .frame(maxWidth: .infinity)
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

    private func fullAddress(_ listing: Listing) -> String {
        [listing.address, listing.city, listing.state, listing.zipCode]
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
    }

    private func startChat(_ listing: Listing) async {
        do {
            openedConversation = try await appState.api.startConversation(listingId: listing.id)
        } catch {
            actionError = error.localizedDescription
        }
    }

    private func confirmAvailability() async {
        confirmingAvailability = true
        do {
            listing = try await appState.api.confirmListing(id: listingId)
        } catch {
            actionError = error.localizedDescription
        }
        confirmingAvailability = false
    }

    private func load() async {
        loading = true
        errorMessage = nil
        do {
            listing = try await appState.api.listing(id: listingId)
            // Price context is a nice-to-have; never fail the screen over it.
            comparison = try? await appState.api.priceComparison(listingId: listingId)
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

/// Answers "is this price reasonable?" — 46% of respondents asked for exactly this.
struct PriceComparisonCard: View {
    let comparison: PriceComparison

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("Price check", systemImage: "chart.bar.xaxis").font(.headline)
                Spacer()
                Text(comparison.verdictLabel)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(tint.opacity(0.15), in: Capsule())
                    .foregroundStyle(tint)
            }

            Text("Compared with \(comparison.sampleSize) similar listings nearby, this is \(differenceText) the median of \(Format.compactPrice(comparison.median)).")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            // A simple range bar showing where this listing sits.
            GeometryReader { geo in
                let span = max(1, comparison.max - comparison.min)
                let ratio = min(max(Double(comparison.price - comparison.min) / Double(span), 0), 1)
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(.tertiarySystemFill)).frame(height: 6)
                    Circle()
                        .fill(tint)
                        .frame(width: 12, height: 12)
                        .offset(x: max(0, ratio * (geo.size.width - 12)))
                }
                .frame(height: 12)
            }
            .frame(height: 12)

            HStack {
                Text(Format.compactPrice(comparison.min))
                Spacer()
                Text(Format.compactPrice(comparison.max))
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(14)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
    }

    private var tint: Color {
        switch comparison.verdict {
        case "below_market": return .cabinForest
        case "above_market": return .cabinClay
        default: return .secondary
        }
    }

    private var differenceText: String {
        let pct = abs(comparison.percentDiff)
        if pct < 1 { return "right at" }
        return String(format: "%.0f%% %@", pct, comparison.percentDiff > 0 ? "above" : "below")
    }
}

/// Reporting flow — 57% of respondents had hit a scam and had nowhere to say so.
struct ReportListingSheet: View {
    let listingId: String
    var onDone: () async -> Void

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var reason = "fake_listing"
    @State private var details = ""
    @State private var submitting = false
    @State private var errorMessage: String?

    private let reasons: [(String, String)] = [
        ("fake_listing", "Fake or doesn't exist"),
        ("scam", "Scam — asks for money upfront"),
        ("wrong_price", "Price is wrong or misleading"),
        ("already_taken", "Already sold or rented"),
        ("misleading_photos", "Photos aren't of this property"),
        ("duplicate", "Duplicate listing"),
        ("offensive", "Offensive content"),
        ("other", "Something else"),
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("What's wrong with this listing?") {
                    Picker("Reason", selection: $reason) {
                        ForEach(reasons, id: \.0) { Text($0.1).tag($0.0) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section("Anything else we should know?") {
                    TextField("Optional details", text: $details, axis: .vertical)
                        .lineLimit(3...6)
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(.red).font(.footnote) }
                }
                Section {
                    Text("Reports go to our moderators. Three open reports send a listing back for re-screening automatically.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Report listing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Submit") { Task { await submit() } }
                        .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        submitting = true
        errorMessage = nil
        do {
            try await appState.api.reportListing(id: listingId, reason: reason, details: details)
            await onDone()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        submitting = false
    }
}
