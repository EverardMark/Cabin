import SwiftUI

/// Someone else's public profile: who they are, whether they're verified, and
/// what people who actually dealt with them said. 39% of respondents asked for
/// agent ratings and reviews.
struct UserProfileView: View {
    let userId: String
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var profile: PublicProfile?
    @State private var reviews: [Review] = []
    @State private var loading = true
    @State private var errorMessage: String?
    @State private var showReviewSheet = false

    var body: some View {
        VStack(spacing: 0) {
            SoftHeader {
                CircleButton(systemImage: "chevron.left") { dismiss() }
            } title: {
                Text(profile?.name ?? "Profile").font(.softScreenTitle).lineLimit(1).padding(.horizontal, 60)
            } trailing: {
                Color.clear.frame(width: 48, height: 48)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if loading {
                        ProgressView().tint(Color.softInk).frame(maxWidth: .infinity).padding(.top, 40)
                    } else if let profile {
                        header(profile)
                        if !profile.bio.isEmpty {
                            SoftCard {
                                Text(profile.bio).font(.soft(16)).foregroundStyle(Color.softTextSoft)
                            }
                        }
                        reviewsSection(profile)
                    } else {
                        SoftEmpty(systemImage: "person.slash", title: "Couldn't load profile", message: errorMessage ?? "")
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 48)
            }
        }
        .softScreen()
        .hidesSoftTabBar()
        .sheet(isPresented: $showReviewSheet) {
            LeaveReviewSheet(userId: userId, userName: profile?.name ?? "") {
                await load()
            }
        }
        .task { await load() }
    }

    private func header(_ profile: PublicProfile) -> some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 16) {
                    SoftAvatar(name: profile.name, size: 72)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(profile.name).font(.softScreenTitle).lineLimit(1)
                        Text(profile.isAgent ? "Real estate agent" : "Member since \(memberSince(profile))")
                            .font(.softSmall).foregroundStyle(Color.softSecondary)
                        HStack(spacing: 6) {
                            if profile.verificationStatus == .verified {
                                VTag(text: "Account verified")
                            } else {
                                OTag(text: "Not verified")
                            }
                            if profile.ratingCount > 0 {
                                OTag(text: String(format: "★ %.1f · %d", profile.ratingAvg, profile.ratingCount))
                            }
                        }
                        .padding(.top, 4)
                    }
                }
                if profile.verificationStatus != .verified {
                    Text("This account hasn't completed identity verification. View in person before paying anything.")
                        .font(.soft(13)).foregroundStyle(Color.softClay)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    private func memberSince(_ profile: PublicProfile) -> String {
        Format.date(from: profile.createdAt)?.formatted(.dateTime.month(.abbreviated).year()) ?? "—"
    }

    @ViewBuilder
    private func reviewsSection(_ profile: PublicProfile) -> some View {
        HStack {
            Text("Reviews").font(.softSmall).foregroundStyle(Color.softSecondary)
            Spacer()
            if profile.id != appState.currentUser?.id {
                SoftLink(title: "Write a review") { showReviewSheet = true }
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 6)

        if reviews.isEmpty {
            Text("No reviews yet. Reviews can only be written by someone who completed a viewing with this person.")
                .font(.soft(14)).foregroundStyle(Color.softSecondary)
                .padding(.horizontal, 8)
        } else {
            ForEach(reviews) { review in
                SoftRow {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(String(repeating: "★", count: max(1, min(5, review.rating))))
                                .font(.soft(14, .regular)).foregroundStyle(Color.softText)
                            Spacer()
                            Text(Format.relative(review.createdAt)).font(.soft(12)).foregroundStyle(Color.softMuted)
                        }
                        if !review.comment.isEmpty {
                            Text(review.comment).font(.soft(15)).foregroundStyle(Color.softTextSoft)
                        }
                        if let author = review.author {
                            Text("— \(author.name)").font(.soft(13)).foregroundStyle(Color.softSecondary)
                        }
                    }
                    .padding(6)
                }
            }
        }
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
