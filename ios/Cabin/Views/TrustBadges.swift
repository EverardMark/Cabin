import SwiftUI

// The survey's dominant signal: 86% called trust & verification "extremely
// important" and 79% wanted verified-only listings. These views are how that
// shows up on screen, and they are deliberate about what the badge claims —
// a screened listing, not a guarantee of ownership.

extension VerificationStatus {
    var tint: Color {
        switch self {
        case .verified:   return .cabinForest
        case .flagged:    return .cabinClay
        case .rejected:   return .red
        case .pending:    return .secondary
        case .unverified: return .secondary
        }
    }
}

/// Compact badge for listing cards and rows.
struct VerificationBadge: View {
    let status: VerificationStatus
    var compact: Bool = false

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: status.symbol)
            if !compact { Text(status.label) }
        }
        .font(.caption2.weight(.semibold))
        .padding(.horizontal, compact ? 5 : 7)
        .padding(.vertical, 3)
        .background(status.tint.opacity(0.15), in: Capsule())
        .foregroundStyle(status.tint)
        .accessibilityLabel("Verification status: \(status.label)")
    }
}

/// Role label. Being an agent is self-declared at signup, so this is only an
/// occupation label — the trust signal is `VerificationBadge`, never this.
struct AgentBadge: View {
    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: "briefcase.fill")
            Text("Agent")
        }
        .font(.caption2.weight(.semibold))
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(Color.secondary.opacity(0.15), in: Capsule())
        .foregroundStyle(.secondary)
    }
}

/// Star rating with the review count, for owner and agent cards.
struct RatingStars: View {
    let rating: Double
    let count: Int

    var body: some View {
        if count == 0 {
            Text("No reviews yet")
                .font(.caption)
                .foregroundStyle(.secondary)
        } else {
            HStack(spacing: 2) {
                ForEach(1...5, id: \.self) { i in
                    Image(systemName: Double(i) <= rating.rounded() ? "star.fill" : "star")
                        .font(.caption2)
                }
                Text(String(format: "%.1f (%d)", rating, count))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.leading, 2)
            }
            .foregroundStyle(Color.cabinClay)
            .accessibilityLabel("Rated \(String(format: "%.1f", rating)) out of 5 from \(count) reviews")
        }
    }
}

/// The full trust panel on a listing detail screen: what the review concluded,
/// why, and what it does not promise.
struct TrustPanel: View {
    let listing: Listing

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: listing.verificationStatus.symbol)
                    .font(.title3)
                    .foregroundStyle(listing.verificationStatus.tint)
                VStack(alignment: .leading, spacing: 1) {
                    Text(headline)
                        .font(.subheadline.weight(.semibold))
                    Text("Trust score \(listing.verificationScore)/100")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            if !listing.verificationSummary.isEmpty {
                Text(listing.verificationSummary)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if !listing.verificationFlags.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(listing.verificationFlags, id: \.self) { flag in
                        Label(Format.flagLabel(flag), systemImage: "exclamationmark.circle")
                            .font(.caption)
                            .foregroundStyle(Color.cabinClay)
                    }
                }
            }

            if listing.reportCount > 0 {
                Label("\(listing.reportCount) user report\(listing.reportCount == 1 ? "" : "s") on this listing",
                      systemImage: "flag.fill")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.red)
            }

            // Say plainly what the badge does and does not mean.
            Text("Listings are screened automatically for scam and quality signals. A badge is not a guarantee of ownership — always view in person before paying anything.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(listing.verificationStatus.tint.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(listing.verificationStatus.tint.opacity(0.25), lineWidth: 1)
        )
    }

    private var headline: String {
        switch listing.verificationStatus {
        case .verified:   return "Screened and verified"
        case .flagged:    return "Verified with warnings"
        case .rejected:   return "Failed screening"
        case .pending:    return "Being screened now"
        case .unverified: return "Not screened yet"
        }
    }
}

/// Warning shown when the owner has not confirmed availability recently —
/// "outdated listings" was one of the most common free-text complaints.
struct StaleWarning: View {
    var body: some View {
        Label("The owner hasn't confirmed this is still available in over a month.",
              systemImage: "clock.badge.exclamationmark")
            .font(.caption)
            .foregroundStyle(Color.cabinClay)
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.cabinClay.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
    }
}

/// Owner / agent card used on the listing detail screen.
struct PosterCard: View {
    let owner: UserSummary
    var onViewProfile: () -> Void

    var body: some View {
        Button(action: onViewProfile) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color.cabinForest.opacity(0.15))
                    Text(initials).font(.headline).foregroundStyle(Color.cabinForest)
                }
                .frame(width: 44, height: 44)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(owner.name).font(.subheadline.weight(.semibold))
                        if owner.isAgent { AgentBadge() }
                    }
                    HStack(spacing: 6) {
                        VerificationBadge(status: owner.verificationStatus)
                        RatingStars(rating: owner.ratingAvg, count: owner.ratingCount)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
            }
            .padding(12)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    private var initials: String {
        let parts = owner.name.split(separator: " ")
        return parts.prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}
