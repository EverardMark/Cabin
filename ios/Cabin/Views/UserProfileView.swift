import SwiftUI

/// Someone else's public profile: who they are, whether they're verified, and
/// what people who actually dealt with them said. 39% of respondents asked for
/// agent ratings and reviews.
struct UserProfileView: View {
    let userId: String
    @Environment(AppState.self) private var appState

    @State private var profile: PublicProfile?
    @State private var reviews: [Review] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showReviewSheet = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if loading {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                } else if let profile {
                    header(profile)
                    if !profile.bio.isEmpty {
                        Text(profile.bio)
                            .font(.subheadline)
                            .foregroundStyle(.primary.opacity(0.85))
                    }
                    reviewsSection(profile)
                } else {
                    ContentUnavailableView(
                        "Couldn't load profile",
                        systemImage: "person.slash",
                        description: Text(errorMessage ?? "")
                    )
                }
            }
            .padding(16)
        }
        .navigationTitle(profile?.name ?? "Profile")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showReviewSheet) {
            LeaveReviewSheet(userId: userId, userName: profile?.name ?? "") {
                await load()
            }
        }
        .task { await load() }
    }

    private func header(_ profile: PublicProfile) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(Color.cabinForest.opacity(0.15))
                    Text(initials(profile.name)).font(.title2.bold()).foregroundStyle(Color.cabinForest)
                }
                .frame(width: 64, height: 64)

                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 6) {
                        Text(profile.name).font(.title3.weight(.semibold))
                        if profile.isAgent { AgentBadge() }
                    }
                    VerificationBadge(status: profile.verificationStatus)
                    RatingStars(rating: profile.ratingAvg, count: profile.ratingCount)
                }
                Spacer()
            }

            if profile.verificationStatus != .verified {
                Label("This account hasn't completed identity verification.",
                      systemImage: "exclamationmark.triangle")
                    .font(.caption)
                    .foregroundStyle(Color.cabinClay)
            }
        }
    }

    @ViewBuilder
    private func reviewsSection(_ profile: PublicProfile) -> some View {
        HStack {
            Text("Reviews").font(.headline)
            Spacer()
            if profile.id != appState.currentUser?.id {
                Button("Write a review") { showReviewSheet = true }
                    .font(.subheadline)
            }
        }
        .padding(.top, 4)

        if reviews.isEmpty {
            Text("No reviews yet. Reviews can only be written by someone who completed a viewing with this person.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else {
            ForEach(reviews) { review in
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 6) {
                        RatingStars(rating: Double(review.rating), count: 1)
                        Spacer()
                        Text(Format.relative(review.createdAt))
                            .font(.caption2).foregroundStyle(.tertiary)
                    }
                    if !review.comment.isEmpty {
                        Text(review.comment).font(.footnote)
                    }
                    if let author = review.author {
                        Text("— \(author.name)").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func initials(_ name: String) -> String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }

    private func load() async {
        loading = true
        do {
            profile = try await appState.api.profile(userId: userId).user
            reviews = (try? await appState.api.reviews(userId: userId).reviews) ?? []
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
        loading = false
    }
}

struct LeaveReviewSheet: View {
    let userId: String
    let userName: String
    var onDone: () async -> Void

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var rating = 5
    @State private var comment = ""
    @State private var submitting = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Your rating") {
                    Picker("Rating", selection: $rating) {
                        ForEach(1...5, id: \.self) { n in
                            Text(String(repeating: "★", count: n)).tag(n)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section("What was it like dealing with \(userName)?") {
                    TextField("Optional comment", text: $comment, axis: .vertical)
                        .lineLimit(3...6)
                }
                if let errorMessage {
                    Section { Text(errorMessage).font(.footnote).foregroundStyle(.red) }
                }
                Section {
                    Text("You can only review someone after completing a viewing with them, which is what keeps these ratings meaningful.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Write a review")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Post") { Task { await submit() } }.disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        submitting = true
        errorMessage = nil
        do {
            _ = try await appState.api.createReview(userId: userId, rating: rating, comment: comment, listingId: "")
            await onDone()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
        submitting = false
    }
}
