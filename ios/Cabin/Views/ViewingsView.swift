import SwiftUI

/// Viewing appointments — 57% of respondents wanted in-app scheduling, and
/// "scheduling viewings" was the fourth-biggest cause of transaction delays.
struct ViewingsView: View {
    @Environment(AppState.self) private var appState

    @State private var viewings: [ViewingRequest] = []
    @State private var loading = true
    @State private var errorMessage: String?

    private var upcoming: [ViewingRequest] {
        viewings.filter { ($0.scheduledDate ?? .distantPast) >= Date() && $0.status != "cancelled" && $0.status != "declined" }
    }
    private var past: [ViewingRequest] {
        viewings.filter { !upcoming.contains($0) }
    }

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                AppMark()
            } title: {
                Text("Viewings").font(.softScreenTitle)
            } trailing: {
                Color.clear.frame(width: 48, height: 48)
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if loading && viewings.isEmpty {
                        ProgressView().tint(Color.softInk).frame(maxWidth: .infinity).padding(.top, 40)
                    } else if viewings.isEmpty {
                        SoftEmpty(systemImage: "calendar", title: "No viewings booked",
                                  message: errorMessage ?? "Request a viewing from any listing and it will show up here.")
                    } else {
                        if !upcoming.isEmpty {
                            sectionLabel("Upcoming")
                            ForEach(upcoming) { ViewingRow(viewing: $0, onUpdate: update) }
                        }
                        if !past.isEmpty {
                            sectionLabel("Past").padding(.top, 8)
                            ForEach(past) { ViewingRow(viewing: $0, onUpdate: update) }
                        }
                        Text("Viewings auto-complete 24 hours after their slot; a completed viewing unlocks leaving a review.")
                            .font(.soft(13)).foregroundStyle(Color.softSecondary)
                            .padding(.horizontal, 8).padding(.top, 16)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 120)
            }
        }
        .softScreen()
        .navigationDestination(for: String.self) { id in
            ListingDetailView(listingId: id)
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text).font(.softSmall).foregroundStyle(Color.softSecondary).padding(.horizontal, 8)
    }

    private func update(_ viewing: ViewingRequest, status: String) async {
        do {
            _ = try await appState.api.updateViewing(id: viewing.id, body: ViewingUpdateBody(status: status))
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func load() async {
        loading = true
        do {
            viewings = try await appState.api.viewings().viewings
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
        await appState.refreshSummary()
    }
}

/// Date tile, title, time · who, inline Accept / Decline for owners, status tag.
struct ViewingRow: View {
    let viewing: ViewingRequest
    var onUpdate: (ViewingRequest, String) async -> Void

    @Environment(AppState.self) private var appState

    private var isOwner: Bool { viewing.ownerId == appState.currentUser?.id }
    private var date: Date? { viewing.scheduledDate }

    var body: some View {
        SoftRow {
            VStack(spacing: 2) {
                Text(date.map { $0.formatted(.dateTime.day()) } ?? "—").font(.soft(24))
                Text(date.map { $0.formatted(.dateTime.month(.abbreviated)) } ?? "").font(.soft(12)).foregroundStyle(Color.softSecondary)
            }
            .frame(width: 64, height: 64)
            .background(Color.softTile, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                NavigationLink(value: viewing.listingId) {
                    Text(viewing.listing?.title ?? "Listing")
                        .font(.softBody)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                .buttonStyle(SoftPressStyle())
                Text("\(timeText) · \(who)")
                    .font(.soft(14)).foregroundStyle(Color.softSecondary)
                    .lineLimit(2)
                if !viewing.note.isEmpty {
                    Text("“\(viewing.note)”").font(.soft(13)).foregroundStyle(Color.softSecondary).lineLimit(2)
                }
                if !viewing.responseNote.isEmpty {
                    Text("Reply: \(viewing.responseNote)").font(.soft(13)).foregroundStyle(Color.softSecondary).lineLimit(2)
                }
                actions
            }

            Spacer(minLength: 4)

            statusTag.padding(.trailing, 2)
        }
    }

    /// "10:00 AM" — the formatter's narrow no-break space has no glyph in Outfit, so swap it for a plain space.
    private var timeText: String {
        (date?.formatted(.dateTime.hour().minute()) ?? "").replacingOccurrences(of: "\u{202F}", with: " ")
    }

    private var who: String {
        isOwner
            ? "\(viewing.requester?.name ?? "A buyer") asked"
            : "with \(viewing.owner?.name ?? "the poster")"
    }

    @ViewBuilder
    private var actions: some View {
        // The owner drives the lifecycle; either side can cancel.
        if isOwner && viewing.status == "requested" {
            HStack(spacing: 8) {
                smallButton("Accept", primary: true) { await onUpdate(viewing, "confirmed") }
                smallButton("Decline", primary: false) { await onUpdate(viewing, "declined") }
            }
            .padding(.top, 8)
        } else if isOwner && viewing.status == "confirmed" {
            HStack(spacing: 8) {
                smallButton("Mark completed", primary: true) { await onUpdate(viewing, "completed") }
                smallButton("Cancel", primary: false) { await onUpdate(viewing, "cancelled") }
            }
            .padding(.top, 8)
        } else if viewing.status == "requested" || viewing.status == "confirmed" {
            HStack(spacing: 8) {
                smallButton("Cancel", primary: false) { await onUpdate(viewing, "cancelled") }
            }
            .padding(.top, 8)
        }
    }

    private func smallButton(_ title: String, primary: Bool, action: @escaping () async -> Void) -> some View {
        Button {
            Task { await action() }
        } label: {
            Text(title)
                .font(.soft(13, .regular))
                .foregroundStyle(primary ? .white : Color.softText)
                .padding(.horizontal, 16).padding(.vertical, 9)
                .background(primary ? Color.softInk : Color.softTile, in: Capsule())
        }
        .buttonStyle(SoftPressStyle())
    }

    @ViewBuilder
    private var statusTag: some View {
        switch viewing.status {
        case "confirmed": VTag(text: "Confirmed")
        case "completed": VTag(text: "Completed")
        case "requested": OTag(text: "Pending")
        case "declined":  OTag(text: "Declined")
        case "cancelled": OTag(text: "Cancelled")
        default:          OTag(text: Format.capitalized(viewing.status))
        }
    }
}

/// Booking sheet shown from a listing.
struct BookViewingSheet: View {
    let listing: Listing

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var date = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
    @State private var note = ""
    @State private var submitting = false
    @State private var errorMessage: String?
    @State private var booked = false

    var body: some View {
        NavigationStack {
            Form {
                Section("When would you like to view it?") {
                    DatePicker("Date and time", selection: $date, in: Date()...,
                               displayedComponents: [.date, .hourAndMinute])
                }
                Section("Note for the poster") {
                    TextField("Optional — e.g. I can only do weekends", text: $note, axis: .vertical)
                        .lineLimit(2...4)
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.footnote).foregroundStyle(.red) }
                }
                Section {
                    Label("The poster has to accept before it's confirmed. Never pay anything before you've seen the property.",
                          systemImage: "info.circle")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Request a viewing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Request") { Task { await submit() } }.disabled(submitting)
                }
            }
            .alert("Viewing requested", isPresented: $booked) {
                Button("OK") { dismiss() }
            } message: {
                Text("We've sent your request. You'll see it under Viewings once the poster responds.")
            }
        }
    }

    private func submit() async {
        submitting = true
        errorMessage = nil
        do {
            _ = try await appState.api.requestViewing(listingId: listing.id, at: date, note: note)
            booked = true
        } catch {
            errorMessage = error.localizedDescription
        }
        submitting = false
    }
}
