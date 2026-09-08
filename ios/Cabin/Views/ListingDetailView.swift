import SwiftUI

struct ListingDetailView: View {
    let listingId: String
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var listing: Listing?
    @State private var comparison: PriceComparison?
    @State private var loading = true
    @State private var errorMessage: String?

    @State private var showReport = false
    @State private var showBooking = false
    @State private var openedConversation: Conversation?
    @State private var actionError: String?
    @State private var confirmingAvailability = false
    @State private var showPromote = false
    @State private var showEdit = false

    private var isMine: Bool { listing?.userId == appState.currentUser?.id }

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                CircleButton(systemImage: "chevron.left") { dismiss() }
            } title: {
                EmptyView()
            } trailing: {
                if listing != nil {
                    if isMine {
                        CircleButton(systemImage: "pencil") { showEdit = true }
                    } else {
                        CircleButton(systemImage: "flag") { showReport = true }
                    }
                }
            }

            if loading {
                Spacer()
                ProgressView().tint(Color.softInk)
                Spacer()
            } else if let listing {
                content(listing)
            } else {
                SoftEmpty(systemImage: "exclamationmark.triangle", title: "Couldn't load listing",
                          message: errorMessage ?? "", actionTitle: "Retry") { Task { await load() } }
                Spacer()
            }
        }
        .softScreen()
        .hidesSoftTabBar()
        .task { await load() }
    }

    @ViewBuilder
    private func content(_ listing: Listing) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                heroCard(listing)

                trustCard(listing)

                HStack(spacing: 12) {
                    SoftTile(systemImage: "bed.double", label: "Bedrooms", value: Format.beds(listing.bedrooms), compact: true)
                    SoftTile(systemImage: "shower", label: "Bathrooms", value: Format.baths(listing.bathrooms), compact: true)
                    SoftTile(systemImage: "square.dashed", label: "Area · sqft",
                             value: listing.areaSqft > 0 ? "\(listing.areaSqft.formatted())" : "—", compact: true)
                }

                if let comparison, comparison.sampleSize >= 3 {
                    PriceComparisonCard(comparison: comparison)
                }

                if !listing.description.isEmpty {
                    SoftCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("About this property").font(.softCardTitle)
                            Text(listing.description)
                                .font(.soft(16)).foregroundStyle(Color.softTextSoft)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }

                if let owner = listing.owner {
                    Text("Listed by")
                        .font(.softSmall).foregroundStyle(Color.softSecondary)
                        .padding(.leading, 8)
                    NavigationLink { UserProfileView(userId: owner.id) } label: {
                        PosterRow(owner: owner)
                    }
                    .buttonStyle(SoftPressStyle())
                }

                if isMine {
                    ownerActions(listing)
                } else {
                    SoftLink(title: "Report this listing", muted: true) { showReport = true }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 4)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 48)
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
        .sheet(isPresented: $showPromote) {
            PromoteListingSheet(listing: listing) { updated in
                self.listing = updated
            }
        }
        .sheet(isPresented: $showEdit) {
            NavigationStack {
                EditListingView(listing: listing) { updated in
                    self.listing = updated
                    Task { await load() }
                }
            }
        }
        .alert("Something went wrong", isPresented: .constant(actionError != nil)) {
            Button("OK") { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
    }

    // MARK: - Hero card: photos, title, tiles, actions

    private func heroCard(_ listing: Listing) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 0) {
                gallery(listing)
                    .frame(height: 300)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: SoftRadius.image, style: .continuous))
                    .overlay(alignment: .topLeading) { PhotoTags(listing: listing).padding(14) }

                HStack(alignment: .top, spacing: 12) {
                    Text(listing.title)
                        .font(.softHeading).tracking(-0.28)
                        .lineSpacing(-2)
                    Spacer(minLength: 0)
                    OTag(text: listing.listingType == "rent" ? "For rent" : "For sale", filled: true)
                        .padding(.top, 6)
                }
                .padding(.top, 22)

                Text(ListingMeta.line(listing))
                    .font(.softSmall).foregroundStyle(Color.softSecondary)
                    .padding(.top, 8)

                let address = fullAddress(listing)
                if !address.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "mappin").font(.system(size: 13, weight: .light))
                        Text(address).font(.soft(14))
                    }
                    .foregroundStyle(Color.softSecondary)
                    .padding(.top, 6)
                }

                if listing.isStale {
                    Text("The owner hasn't confirmed this is still available in over a month.")
                        .font(.soft(14)).foregroundStyle(Color.softClay)
                        .padding(.top, 8)
                }

                HStack(spacing: 12) {
                    SoftTile(systemImage: "tag", label: "Asking price",
                             value: Format.compactPrice(listing.price) + (listing.listingType == "rent" ? "/mo" : ""))
                    SoftTile(systemImage: "checkmark.shield", label: "Trust score", value: "\(listing.verificationScore)")
                }
                .padding(.top, 20)

                if isMine {
                    HStack(spacing: 10) {
                        PrimaryButton(title: "Edit listing", systemImage: "pencil") { showEdit = true }
                        SecondaryButton(title: listing.isFeatured ? "Extend featuring" : "Feature it",
                                        systemImage: "star", tint: .softTile) { showPromote = true }
                            .disabled(listing.verificationStatus != .verified)
                            .opacity(listing.verificationStatus == .verified ? 1 : 0.45)
                    }
                    .padding(.top, 16)
                } else {
                    HStack(spacing: 10) {
                        PrimaryButton(title: "Message \(listing.owner?.name.split(separator: " ").first.map(String.init) ?? "poster")") {
                            Task { await startChat(listing) }
                        }
                        SecondaryButton(title: "Book a viewing", tint: .softTile) { showBooking = true }
                    }
                    .padding(.top, 16)
                }
            }
        }
    }

    // MARK: - Trust panel

    private func trustCard(_ listing: Listing) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle().fill(trustFill(listing.verificationStatus))
                        Image(systemName: listing.verificationStatus.symbol)
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(trustGlyph(listing.verificationStatus))
                    }
                    .frame(width: 44, height: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(trustHeadline(listing.verificationStatus)).font(.softCardTitle)
                        Text("Trust score \(listing.verificationScore)/100 · screened")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                    }
                }

                if !listing.verificationSummary.isEmpty {
                    Text(listing.verificationSummary)
                        .font(.soft(15)).foregroundStyle(Color.softTextSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if !listing.verificationFlags.isEmpty {
                    FlowTags(tags: listing.verificationFlags.map(Format.flagLabel))
                }

                if listing.reportCount > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "flag.fill").font(.system(size: 12))
                        Text("\(listing.reportCount) user report\(listing.reportCount == 1 ? "" : "s") on this listing")
                            .font(.soft(14, .regular))
                    }
                    .foregroundStyle(Color.softRed)
                }

                // Say plainly what the badge does and does not mean.
                Text("Screened automatically for scam and quality signals. A badge is not proof of ownership — view in person before paying anything.")
                    .font(.soft(13)).foregroundStyle(Color.softSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func trustHeadline(_ status: VerificationStatus) -> String {
        switch status {
        case .verified:   return "Screened and verified"
        case .flagged:    return "Verified with warnings"
        case .rejected:   return "Failed screening"
        case .pending:    return "Being screened now"
        case .unverified: return "Not screened yet"
        }
    }

    private func trustFill(_ status: VerificationStatus) -> Color {
        switch status {
        case .verified: return .softInk
        case .flagged:  return .softClay
        case .rejected: return .softRed
        default:        return .softTile
        }
    }

    private func trustGlyph(_ status: VerificationStatus) -> Color {
        switch status {
        case .verified, .flagged, .rejected: return .white
        default: return .softTextSoft
        }
    }

    // MARK: - Owner tools

    @ViewBuilder
    private func ownerActions(_ listing: Listing) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Keep it fresh").font(.softCardTitle)
                if listing.isFeatured, let until = Format.date(from: listing.featuredUntil ?? "") {
                    HStack(spacing: 6) {
                        Image(systemName: "star").font(.system(size: 12))
                        Text("Featured until \(until.formatted(.dateTime.day().month(.abbreviated)))")
                            .font(.soft(14, .regular))
                    }
                    .foregroundStyle(Color.softTextSoft)
                }
                if listing.verificationStatus != .verified {
                    Text("Only verified listings can be featured. Featuring buys placement, not a badge — so a listing has to pass screening first.")
                        .font(.soft(13)).foregroundStyle(Color.softSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Text(listing.isStale
                     ? "Buyers are shown a warning on listings that haven't been confirmed recently."
                     : "Confirming availability keeps the stale warning off your listing.")
                    .font(.soft(13)).foregroundStyle(Color.softSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                SecondaryButton(title: "Confirm it's still available", systemImage: "checkmark.circle",
                                tint: .softTile, loading: confirmingAvailability) {
                    Task { await confirmAvailability() }
                }
            }
        }
    }

    private func gallery(_ listing: Listing) -> some View {
        Group {
            if listing.images.isEmpty {
                ZStack {
                    SoftPhotoPlaceholder()
                    VStack(spacing: 6) {
                        Image(systemName: "photo").font(.system(size: 28, weight: .light))
                        Text("No photos yet").font(.soft(14))
                    }
                    .foregroundStyle(Color.softText.opacity(0.6))
                }
            } else {
                TabView {
                    ForEach(listing.images) { image in
                        RemoteImage(url: image.url).frame(maxWidth: .infinity)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: listing.images.count > 1 ? .automatic : .never))
            }
        }
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

/// Owner / agent row on the listing detail screen.
struct PosterRow: View {
    let owner: UserSummary

    var body: some View {
        SoftRow {
            SoftAvatar(name: owner.name, size: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text(owner.name).font(.softBody)
                HStack(spacing: 6) {
                    if owner.isVerified {
                        VTag(text: "Verified")
                    } else {
                        OTag(text: "Not verified")
                    }
                    if owner.isAgent { OTag(text: "Agent") }
                    if owner.ratingCount > 0 {
                        OTag(text: String(format: "★ %.1f · %d", owner.ratingAvg, owner.ratingCount))
                    }
                }
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .light))
                .foregroundStyle(Color.softMuted)
                .padding(.trailing, 4)
        }
    }
}

/// Wrapping row of outlined tags (verification flags).
struct FlowTags: View {
    let tags: [String]

    var body: some View {
        var width: CGFloat = 0
        var height: CGFloat = 0
        return GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                ForEach(Array(tags.enumerated()), id: \.offset) { index, tag in
                    OTag(text: tag)
                        .padding([.trailing, .bottom], 6)
                        .alignmentGuide(.leading) { d in
                            if abs(width - d.width) > geo.size.width { width = 0; height -= d.height }
                            let result = width
                            if index == tags.count - 1 { width = 0 } else { width -= d.width }
                            return result
                        }
                        .alignmentGuide(.top) { _ in
                            let result = height
                            if index == tags.count - 1 { height = 0 }
                            return result
                        }
                }
            }
        }
        .frame(height: CGFloat((tags.count + 2) / 3) * 34)
    }
}

/// Answers "is this price reasonable?" — 46% of respondents asked for exactly this.
struct PriceComparisonCard: View {
    let comparison: PriceComparison

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("Price check").font(.softCardTitle)
                    Spacer()
                    if comparison.verdict == "below_market" {
                        VTag(text: comparison.verdictLabel)
                    } else {
                        OTag(text: comparison.verdictLabel)
                    }
                }

                Text("Compared with \(comparison.sampleSize) similar listings nearby, this is \(differenceText) the median of \(Format.compactPrice(comparison.median)).")
                    .font(.soft(15)).foregroundStyle(Color.softTextSoft)
                    .fixedSize(horizontal: false, vertical: true)

                GeometryReader { geo in
                    let span = max(1, comparison.max - comparison.min)
                    let ratio = min(max(Double(comparison.price - comparison.min) / Double(span), 0), 1)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.softTile).frame(height: 8)
                        Circle()
                            .fill(Color.softInk)
                            .frame(width: 14, height: 14)
                            .offset(x: max(0, ratio * (geo.size.width - 14)))
                    }
                    .frame(height: 14)
                }
                .frame(height: 14)

                HStack {
                    Text(Format.compactPrice(comparison.min))
                    Spacer()
                    Text(Format.compactPrice(comparison.max))
                }
                .font(.soft(13)).foregroundStyle(Color.softSecondary)
            }
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

/// Buying promoted placement for a listing you own.
///
/// Featured listings were the survey's one unanimous supply-side ask — every
/// agent picked it (6/6), and 58% of owners did. It is sold per listing rather
/// than per month because most posters here have a single property.
struct PromoteListingSheet: View {
    let listing: Listing
    var onPromoted: (Listing) -> Void

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var plans: [FeaturePlan] = []
    @State private var note = ""
    @State private var selected: String?
    @State private var loading = true
    @State private var submitting = false
    @State private var errorMessage: String?
    @State private var unpaidNotice = false

    var body: some View {
        NavigationStack {
            Form {
                if loading {
                    HStack { Spacer(); ProgressView(); Spacer() }
                } else {
                    Section("Choose a package") {
                        ForEach(plans) { plan in
                            Button {
                                selected = plan.id
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(plan.label).font(.subheadline.weight(.medium))
                                        Text("\(plan.days) days of promoted placement")
                                            .font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(Format.price(plan.price, listingType: "sale"))
                                        .font(.subheadline.weight(.semibold))
                                    Image(systemName: selected == plan.id ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(selected == plan.id ? Color.softInk : .secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if !note.isEmpty {
                        Section {
                            Text(note).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }

                if let errorMessage {
                    Section { Text(errorMessage).font(.footnote).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Feature listing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Continue") { Task { await promote() } }
                        .disabled(selected == nil || submitting)
                }
            }
            .alert("Promotion active", isPresented: $unpaidNotice) {
                Button("OK") { dismiss() }
            } message: {
                Text("Your listing is now featured. No payment was taken — checkout isn't connected yet.")
            }
            .task { await loadPlans() }
        }
    }

    private func loadPlans() async {
        do {
            let res = try await appState.api.featurePlans()
            plans = res.plans
            note = res.note
            selected = res.plans.first?.id
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }

    private func promote() async {
        guard let planId = selected else { return }
        submitting = true
        errorMessage = nil
        do {
            let res = try await appState.api.featureListing(id: listing.id, planId: planId)
            onPromoted(res.listing)
            if res.paid {
                dismiss()
            } else {
                // Be honest rather than implying money changed hands.
                unpaidNotice = true
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        submitting = false
    }
}
