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
        Group {
            if loading && viewings.isEmpty {
                ProgressView()
            } else if viewings.isEmpty {
                ContentUnavailableView {
                    Label("No viewings booked", systemImage: "calendar")
                } description: {
                    Text(errorMessage ?? "Request a viewing from any listing and it will show up here.")
                }
            } else {
                List {
                    if !upcoming.isEmpty {
                        Section("Upcoming") {
                            ForEach(upcoming) { row($0) }
                        }
                    }
                    if !past.isEmpty {
                        Section("Past") {
                            ForEach(past) { row($0) }
                        }
                    }
                }
            }
        }
        .navigationTitle("Viewings")
        .task { await load() }
        .refreshable { await load() }
    }

    @ViewBuilder
    private func row(_ viewing: ViewingRequest) -> some View {
        let isOwner = viewing.ownerId == appState.currentUser?.id

        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                RemoteImage(url: viewing.listing?.images.first?.url)
                    .frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 2) {
                    Text(viewing.listing?.title ?? "Listing").font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(Format.dateTime(viewing.scheduledFor)).font(.caption).foregroundStyle(.secondary)
                    Text(isOwner
                         ? "Requested by \(viewing.requester?.name ?? "a buyer")"
                         : "With \(viewing.owner?.name ?? "the poster")")
                        .font(.caption2).foregroundStyle(.tertiary)
                }
                Spacer()
                StatusChip(status: viewing.status)
            }

            if !viewing.note.isEmpty {
                Text("“\(viewing.note)”").font(.caption).foregroundStyle(.secondary)
            }
            if !viewing.responseNote.isEmpty {
                Text("Reply: \(viewing.responseNote)").font(.caption).foregroundStyle(.secondary)
            }

            // The owner drives the lifecycle; either side can cancel.
            HStack(spacing: 8) {
                if isOwner && viewing.status == "requested" {
                    actionButton("Accept", .cabinForest) { await update(viewing, status: "confirmed") }
                    actionButton("Decline", .secondary) { await update(viewing, status: "declined") }
                }
                if isOwner && viewing.status == "confirmed" {
                    actionButton("Mark completed", .cabinForest) { await update(viewing, status: "completed") }
                }
                if viewing.status == "requested" || viewing.status == "confirmed" {
                    actionButton("Cancel", .red) { await update(viewing, status: "cancelled") }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func actionButton(_ title: String, _ tint: Color, action: @escaping () async -> Void) -> some View {
        Button(title) { Task { await action() } }
            .font(.caption.weight(.semibold))
            .buttonStyle(.bordered)
            .tint(tint)
            .controlSize(.small)
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

struct StatusChip: View {
    let status: String

    var body: some View {
        Text(label)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(tint.opacity(0.15), in: Capsule())
            .foregroundStyle(tint)
    }

    private var label: String {
        switch status {
        case "requested": return "Pending"
        case "confirmed": return "Confirmed"
        case "declined":  return "Declined"
        case "cancelled": return "Cancelled"
        case "completed": return "Completed"
        default:          return Format.capitalized(status)
        }
    }

    private var tint: Color {
        switch status {
        case "confirmed": return .cabinForest
        case "completed": return .cabinForest
        case "declined", "cancelled": return .red
        default: return .cabinClay
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
